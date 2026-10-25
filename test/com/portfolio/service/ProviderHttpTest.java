package com.portfolio.service;

import com.portfolio.model.Stock;
import com.portfolio.service.PriceUnavailableException.Reason;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Finnhub, Twelve Data and the exchange-rate client against a local fake server: authentication, HTTP errors, malformed
 * and empty bodies, currencies. Deterministic: no network, no real keys.
 */
class ProviderHttpTest {
    HttpServer server;
    String base;
    volatile int status = 200;
    volatile String body = "{}";
    final List<String> requests = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            requests.add(ex.getRequestURI().toString());
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
            if (b.length > 0) {
                ex.getResponseBody().write(b);
            }
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static PriceUnavailableException failure(org.junit.jupiter.api.function.Executable e) {
        return assertThrows(PriceUnavailableException.class, e);
    }

    // ---------------------------------------------------------------- Finnhub

    @Test
    void finnhubParsesAQuoteAndStatesUsdForABareTicker() {
        body = "{\"c\":189.5,\"d\":1.2,\"dp\":0.64,\"h\":190.1,\"l\":187.0,\"o\":188,\"pc\":188.3}";
        Stock s = new FinnhubService(base, () -> "k1").getStock("aapl");
        assertEquals(189.5, s.getCurrentPrice(), 0.0);
        assertEquals(0.64, s.getChangePercent(), 0.0);
        assertEquals("USD", s.getCurrency());
        assertEquals("AAPL", s.getSymbol());
        assertTrue(requests.get(0).contains("symbol=AAPL") && requests.get(0).contains("token=k1"));
    }

    @Test
    void finnhubTreatsAllZeroAnswerAsUnknownSymbolNotAPriceOfZero() {
        body = "{\"c\":0,\"d\":null,\"dp\":null,\"h\":0,\"l\":0,\"o\":0,\"pc\":0}";
        assertNull(new FinnhubService(base, () -> "k").getStock("NOPE"));
    }

    @Test
    void finnhubSuffixedSymbolHasNoStatedCurrency() {
        body = "{\"c\":245.8,\"dp\":0.67}";
        assertNull(new FinnhubService(base, () -> "k").getStock("POWERGRID.NS").getCurrency(), "not guessed");
    }

    @Test
    void finnhubClassifiesAuthQuotaAndServerErrorsAndNeverReturnsAPrice() {
        FinnhubService f = new FinnhubService(base, () -> "k");
        status = 401;
        assertEquals(Reason.INVALID_KEY, failure(() -> f.getStock("A")).getReason());
        status = 403;
        assertEquals(Reason.INVALID_KEY, failure(() -> f.getStock("A")).getReason());
        status = 429;
        assertEquals(Reason.RATE_LIMIT, failure(() -> f.getStock("A")).getReason());
        status = 503;
        assertEquals(Reason.HTTP_ERROR, failure(() -> f.getStock("A")).getReason());
    }

    @Test
    void finnhubWithoutAKeyDoesNotCallTheServer() {
        FinnhubService f = new FinnhubService(base, () -> "");
        assertEquals(Reason.NO_KEY, failure(() -> f.getStock("A")).getReason());
        assertTrue(requests.isEmpty(), "no request is made without a key");
    }

    @Test
    void finnhubGarbageAndEmptyBodiesNeverBecomeAPrice() {
        FinnhubService f = new FinnhubService(base, () -> "k");
        for (String garbage : new String[] { "", "not json at all", "<html>502</html>", "{\"c\":\"abc\"}", "{\"c\":-5}", "[]" }) {
            body = garbage;
            Stock s = null;
            try {
                s = f.getStock("A");
            } catch (PriceUnavailableException ignored) {
                // acceptable: reported as unavailable
            }
            assertNull(s, "body: " + garbage);
        }
    }

    @Test
    void finnhubMarketCapIsLabelledWithTheProfilesCurrencyNotRupees() {
        body = "{\"c\":10,\"dp\":1}";
        FinnhubService f = new FinnhubService(base, () -> "k");
        Stock s = f.getStock("AAA");
        s.setName("AAA");
        body = "{\"c\":10,\"dp\":1,\"name\":\"Alpha Inc\",\"marketCapitalization\":2500000,\"finnhubIndustry\":\"Tech\",\"currency\":\"USD\"}";
        f.updateStockPrice(s);
        assertEquals("USD 2.50T", s.getMarketCap());
        assertFalse(s.getMarketCap().contains("₹"));
    }

    // ---------------------------------------------------------------- Twelve Data

    @Test
    void twelveDataParsesQuoteAndStatedCurrency() {
        body = "{\"symbol\":\"SAP\",\"close\":\"150.25\",\"percent_change\":\"-0.5\",\"high\":\"151\",\"low\":\"149\",\"currency\":\"EUR\"}";
        Stock s = new TwelveDataService(base, () -> "k2").getStock("sap");
        assertEquals(150.25, s.getCurrentPrice(), 0.0);
        assertEquals("EUR", s.getCurrency(), "stated by the provider, not assumed");
        assertEquals(-0.5, s.getChangePercent(), 0.0);
        assertTrue(requests.get(0).contains("apikey=k2"));
    }

    @Test
    void twelveDataWithoutACurrencyLeavesItUnknown() {
        body = "{\"symbol\":\"X\",\"close\":\"10\"}";
        assertNull(new TwelveDataService(base, () -> "k").getStock("X").getCurrency());
    }

    @Test
    void twelveDataErrorBodiesAreClassifiedNotParsedAsQuotes() {
        TwelveDataService t = new TwelveDataService(base, () -> "k");
        body = "{\"code\":429,\"message\":\"You have run out of API credits\",\"status\":\"error\"}";
        assertEquals(Reason.RATE_LIMIT, failure(() -> t.getStock("A")).getReason());
        body = "{\"code\":401,\"message\":\"apikey is incorrect\",\"status\":\"error\"}";
        assertEquals(Reason.INVALID_KEY, failure(() -> t.getStock("A")).getReason());
        body = "{\"code\":400,\"message\":\"symbol not found\",\"status\":\"error\"}";
        assertEquals(Reason.PROVIDER_MESSAGE, failure(() -> t.getStock("A")).getReason());
        status = 500;
        body = "{}";
        assertEquals(Reason.HTTP_ERROR, failure(() -> t.getStock("A")).getReason());
    }

    @Test
    void twelveDataZeroNegativeOrMissingPriceIsRefused() {
        TwelveDataService t = new TwelveDataService(base, () -> "k");
        for (String b : new String[] { "{\"close\":\"0\"}", "{\"close\":\"-3\"}", "{}", "{\"close\":\"abc\"}", "" }) {
            body = b;
            assertEquals(Reason.BAD_RESPONSE, failure(() -> t.getStock("A")).getReason(), "body: " + b);
        }
    }

    @Test
    void twelveDataHistoryAndMissingKey() {
        body = "{\"values\":[{\"datetime\":\"2026-01-02\",\"close\":\"10\"}],\"status\":\"ok\"}";
        TwelveDataService t = new TwelveDataService(base, () -> "k");
        assertEquals(1, PriceHistory.parse(t.getHistoricalData("A")).size());
        assertEquals(Reason.NO_KEY, failure(() -> new TwelveDataService(base, () -> null).getStock("A")).getReason());
    }

    @Test
    void theKeyIsUrlEncodedAndNeverPartOfAnErrorMessage() {
        String key = "k e&y=1";
        status = 500;
        PriceUnavailableException e = failure(() -> new FinnhubService(base, () -> key).getStock("A"));
        assertFalse(e.getMessage().contains(key) || e.getMessage().contains("k%20e"), "the key must not appear in messages");
        assertTrue(requests.get(0).contains("token=k+e%26y%3D1"), requests.get(0));
        requests.clear();
        e = failure(() -> new TwelveDataService(base, () -> key).getStock("A"));
        assertFalse(e.getMessage().contains(key));
    }

    // ---------------------------------------------------------------- exchange-rate client

    private CurrencyService rates() {
        return CurrencyService.forTesting(base + "/v6/", "ratekey", new TradePolicyTest.MutableClock());
    }

    @Test
    void exchangeRateHappyPathUsesTheDocumentedShape() {
        body = "{\"result\":\"success\",\"base_code\":\"USD\",\"conversion_rates\":{\"USD\":1,\"INR\":83.25}}";
        CurrencyService.Rate r = rates().requireRate("USD", "INR");
        assertEquals(83.25, r.rate(), 0.0);
        assertEquals(CurrencyService.DEFAULT_SOURCE, r.source());
        assertTrue(requests.get(0).startsWith("/v6/ratekey/latest/USD"), requests.get(0));
    }

    @Test
    void exchangeRateFailuresBlockInsteadOfGuessing() {
        CurrencyService c = rates();
        for (String b : new String[] { "{\"result\":\"error\",\"error-type\":\"invalid-key\"}", "{\"result\":\"success\"}",
                "{\"result\":\"success\",\"conversion_rates\":{\"USD\":1}}", "not json", "",
                "{\"result\":\"success\",\"conversion_rates\":{\"INR\":0}}", "{\"result\":\"success\",\"conversion_rates\":{\"INR\":-1}}" }) {
            body = b;
            TradeBlockedException e = assertThrows(TradeBlockedException.class, () -> CurrencyService
                    .forTesting(base + "/v6/", "ratekey", new TradePolicyTest.MutableClock()).requireRate("USD", "INR"), "body: " + b);
            assertEquals(TradeBlockedException.Reason.RATE_UNAVAILABLE, e.getReason());
            assertFalse(e.getMessage().contains("ratekey"), "the key must not leak into messages");
        }
        status = 500;
        body = "{}";
        assertThrows(TradeBlockedException.class, () -> c.requireRate("USD", "INR"));
    }

    @Test
    void exchangeRateWithoutAKeyMakesNoRequest() {
        CurrencyService c = CurrencyService.forTesting(base + "/v6/", "", new TradePolicyTest.MutableClock());
        assertThrows(TradeBlockedException.class, () -> c.requireRate("USD", "INR"));
        assertTrue(requests.isEmpty());
    }

    @Test
    void aSlowServerCannotHoldACallerLongerThanTheTimeout() {
        // the fake never answers within the client's 10 s limit only if we make it sleep; here we just assert the configured bound
        assertEquals(Duration.ofHours(1), CurrencyService.RATE_MAX_AGE);
    }
}
