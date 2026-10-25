package com.portfolio.service;

import com.portfolio.model.Stock;
import com.portfolio.service.SymbolResolver.Exchange;
import com.portfolio.service.SymbolResolver.Instrument;
import com.portfolio.service.SymbolResolver.Provider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Scripted providers only: nothing here touches a real API. */
class SymbolResolutionTest {

    // ------------------------------------------------------------------ parsing and mapping

    @Test
    void storedSpellingsAreUnderstoodAndBareSymbolsStayAmbiguous() {
        assertEquals(new Instrument("RELIANCE", Exchange.NSE), SymbolResolver.parse("reliance.ns"));
        assertEquals(new Instrument("RELIANCE", Exchange.NSE), SymbolResolver.parse("RELIANCE.NSE"));
        assertEquals(new Instrument("RELIANCE", Exchange.BSE), SymbolResolver.parse("RELIANCE.BSE"));
        assertEquals(new Instrument("RELIANCE", Exchange.BSE), SymbolResolver.parse("RELIANCE.BO"));
        assertEquals(new Instrument("TCS", Exchange.NSE), SymbolResolver.parse("TCS:NSE"));
        assertEquals(new Instrument("AAPL", Exchange.UNSPECIFIED), SymbolResolver.parse(" aapl "), "no exchange is ever guessed");
        assertEquals(new Instrument("BRK-B", Exchange.UNSPECIFIED), SymbolResolver.parse("BRK-B"));
        assertEquals("RELIANCE.NS", SymbolResolver.parse("RELIANCE.NSE").canonical());
        assertEquals("RELIANCE.BSE", SymbolResolver.parse("RELIANCE.BO").canonical());
    }

    @Test
    void emptyAndMalformedSymbolsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> SymbolResolver.parse(null));
        assertThrows(IllegalArgumentException.class, () -> SymbolResolver.parse("  "));
        assertThrows(IllegalArgumentException.class, () -> SymbolResolver.parse("AA PL"));
        assertThrows(IllegalArgumentException.class, () -> SymbolResolver.parse("DROP;TABLE"));
        assertThrows(IllegalArgumentException.class, () -> SymbolResolver.parse("A".repeat(40)));
    }

    @Test
    void eachProviderGetsItsOwnDocumentedSpellingOrAnHonestNo() {
        Instrument bse = SymbolResolver.parse("RELIANCE.BSE");
        Instrument nse = SymbolResolver.parse("RELIANCE.NS");
        Instrument bare = SymbolResolver.parse("AAPL");
        assertEquals(Optional.of("RELIANCE.BSE"), SymbolResolver.forProvider(bse, Provider.ALPHA_VANTAGE));
        assertEquals(Optional.of("RELIANCE:BSE"), SymbolResolver.forProvider(bse, Provider.TWELVE_DATA));
        assertEquals(Optional.of("RELIANCE.BO"), SymbolResolver.forProvider(bse, Provider.FINNHUB));
        assertEquals(Optional.empty(), SymbolResolver.forProvider(nse, Provider.ALPHA_VANTAGE), "AV lists no NSE: never silently use the BSE listing");
        assertEquals(Optional.of("RELIANCE:NSE"), SymbolResolver.forProvider(nse, Provider.TWELVE_DATA));
        assertEquals(Optional.of("RELIANCE.NS"), SymbolResolver.forProvider(nse, Provider.FINNHUB));
        for (Provider p : Provider.values()) {
            assertEquals(Optional.of("AAPL"), SymbolResolver.forProvider(bare, p), "a bare symbol is sent unchanged (legacy behaviour)");
        }
    }

    // ------------------------------------------------------------------ the provider chain

    private static final class Recorder {
        final List<String> quoteCalls = new CopyOnWriteArrayList<>();
        final List<String> historyCalls = new CopyOnWriteArrayList<>();
        final List<Integer> historyDays = new CopyOnWriteArrayList<>();
    }

    private static Stock quote(String symbol, double price) {
        Stock s = new Stock(symbol, symbol, price);
        s.setCurrency("INR");
        return s;
    }

    /** Alpha Vantage stand-in that only knows RELIANCE.BSE. */
    private static AlphaVantageService alpha(Recorder r) {
        return new AlphaVantageService("test-key") {
            @Override public Stock getStock(String symbol) {
                r.quoteCalls.add("AV:" + symbol);
                return "RELIANCE.BSE".equals(symbol) ? quote(symbol, 2914.5) : null;
            }

            @Override public String getHistoricalData(String symbol, int days) {
                r.historyCalls.add("AV:" + symbol);
                r.historyDays.add(days);
                if (!"RELIANCE.BSE".equals(symbol)) {
                    throw new PriceUnavailableException(PriceUnavailableException.Reason.SYMBOL_NOT_FOUND, "no data");
                }
                return "{\"values\":[{\"datetime\":\"2026-03-02\",\"close\":\"2900\"},{\"datetime\":\"2026-03-01\",\"close\":\"2890\"}]}";
            }
        };
    }

    private static TwelveDataService twelve(Recorder r) {
        return new TwelveDataService() {
            @Override public Stock getStock(String symbol) {
                r.quoteCalls.add("TD:" + symbol);
                throw new PriceUnavailableException(PriceUnavailableException.Reason.UNSUPPORTED, "plan");
            }

            @Override public String getHistoricalData(String symbol, int days) {
                r.historyCalls.add("TD:" + symbol);
                r.historyDays.add(days);
                throw new PriceUnavailableException(PriceUnavailableException.Reason.UNSUPPORTED, "plan");
            }
        };
    }

    private static FinnhubService finnhub(Recorder r) {
        return new FinnhubService() {
            @Override public Stock getStock(String symbol) {
                r.quoteCalls.add("FH:" + symbol);
                throw new PriceUnavailableException(PriceUnavailableException.Reason.UNSUPPORTED, "no access");
            }
        };
    }

    @Test
    void aBseHoldingReachesTheProviderThatOnlyRecognisesTheSuffixForm() {
        Recorder r = new Recorder();
        MultiSourceStockService chain = new MultiSourceStockService(finnhub(r), twelve(r), alpha(r));
        Stock q = chain.getStock("RELIANCE.BSE");
        assertNotNull(q);
        assertEquals("RELIANCE.BSE", q.getSymbol(), "the quote is filed under the symbol the portfolio stores");
        assertTrue(r.quoteCalls.contains("AV:RELIANCE.BSE"), r.quoteCalls.toString());
        assertTrue(r.quoteCalls.contains("TD:RELIANCE:BSE"), "Twelve Data was asked in its own spelling: " + r.quoteCalls);
        assertTrue(r.quoteCalls.contains("FH:RELIANCE.BO"), r.quoteCalls.toString());
    }

    @Test
    void theBoSpellingIsTheSameListingAsBse() {
        Recorder r = new Recorder();
        Stock q = new MultiSourceStockService(finnhub(r), twelve(r), alpha(r)).getStock("RELIANCE.BO");
        assertNotNull(q);
        assertEquals("RELIANCE.BO", q.getSymbol());
        assertTrue(r.quoteCalls.contains("AV:RELIANCE.BSE"));
    }

    @Test
    void anNseListingIsNeverAskedOfAProviderThatHasNoNse() {
        Recorder r = new Recorder();
        MultiSourceStockService chain = new MultiSourceStockService(finnhub(r), twelve(r), alpha(r));
        PriceUnavailableException e = assertThrows(PriceUnavailableException.class, () -> chain.getStock("RELIANCE.NS"));
        assertFalse(r.quoteCalls.stream().anyMatch(c -> c.startsWith("AV:")), "Alpha Vantage is skipped, not given a guessed .BSE: " + r.quoteCalls);
        assertTrue(r.quoteCalls.contains("TD:RELIANCE:NSE") && r.quoteCalls.contains("FH:RELIANCE.NS"), r.quoteCalls.toString());
        assertNotNull(e.getMessage());
    }

    @Test
    void legacyBareSymbolsAreSentAsEnteredAndMalformedOnesFailCleanly() {
        Recorder r = new Recorder();
        MultiSourceStockService chain = new MultiSourceStockService(finnhub(r), twelve(r), alpha(r));
        assertThrows(PriceUnavailableException.class, () -> chain.getStock("AAPL"), "no scripted provider has it");
        assertTrue(r.quoteCalls.contains("AV:AAPL") && r.quoteCalls.contains("TD:AAPL") && r.quoteCalls.contains("FH:AAPL"), r.quoteCalls.toString());
        assertThrows(PriceUnavailableException.class, () -> chain.getStock("NOT A SYMBOL"));
    }

    @Test
    void historyUsesTheSameMappingAndPassesTheRequestedDepthThrough() {
        Recorder r = new Recorder();
        MultiSourceStockService chain = new MultiSourceStockService(finnhub(r), twelve(r), alpha(r));
        String json = chain.getHistoricalData("RELIANCE.BSE", 400);
        assertEquals(2, PriceHistory.parse(json).size());
        assertEquals(List.of("TD:RELIANCE:BSE", "AV:RELIANCE.BSE"), r.historyCalls);
        assertEquals(List.of(400, 400), r.historyDays);
    }

    // ------------------------------------------------------------------ Alpha Vantage full history

    @Test
    void theFullSeriesIsRequestedAndAPlanRefusalFallsBackToCompactOnce() throws Exception {
        List<String> queries = new CopyOnWriteArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/query", ex -> {
            String q = ex.getRequestURI().getRawQuery();
            queries.add(q);
            calls.incrementAndGet();
            String body = q.contains("outputsize=full")
                    ? "{\"Information\":\"Thank you for using Alpha Vantage! The outputsize=full parameter value is a premium feature for the TIME_SERIES_DAILY endpoint.\"}"
                    : "{\"Time Series (Daily)\":{\"2026-03-02\":{\"4. close\":\"101.0\"},\"2026-03-01\":{\"4. close\":\"100.0\"}}}";
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            try (var out = ex.getResponseBody()) {
                out.write(b);
            }
        });
        server.start();
        try {
            AlphaVantageService av = new AlphaVantageService("test-key", "http://127.0.0.1:" + server.getAddress().getPort() + "/query",
                    Duration.ofSeconds(5));
            assertEquals(2, PriceHistory.parse(av.getHistoricalData("RELIANCE.BSE", 400)).size());
            assertTrue(queries.get(0).contains("outputsize=full"), "the full series is tried first");
            assertTrue(queries.get(1).contains("outputsize=compact"), "then the compact series the plan allows");
            int before = calls.get();
            av.getHistoricalData("TCS.BSE", 400);
            assertEquals(before + 1, calls.get(), "the refusal is remembered: later calls go straight to compact");
            assertTrue(queries.get(queries.size() - 1).contains("outputsize=compact"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aRealQuotaMessageIsNotMistakenForAPlanRefusal() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/query", ex -> {
            byte[] b = "{\"Information\":\"Our standard API rate limit is 25 requests per day.\"}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            try (var out = ex.getResponseBody()) {
                out.write(b);
            }
        });
        server.start();
        try {
            AlphaVantageService av = new AlphaVantageService("test-key", "http://127.0.0.1:" + server.getAddress().getPort() + "/query",
                    Duration.ofSeconds(5));
            PriceUnavailableException e = assertThrows(PriceUnavailableException.class, () -> av.getHistoricalData("X.BSE", 400));
            assertEquals(PriceUnavailableException.Reason.RATE_LIMIT, e.getReason());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aSeriesOfAYearIsParsedInFullAndATruncatedOneKeepsOnlyWhatWasReturned() {
        StringBuilder sb = new StringBuilder("{\"values\":[");
        java.time.LocalDate d = java.time.LocalDate.of(2026, 1, 1);
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 380; i++) {
            rows.add("{\"datetime\":\"" + d.minusDays(i) + "\",\"close\":\"" + (100 + i % 7) + "\"}");
        }
        sb.append(String.join(",", rows)).append("]}");
        List<PriceHistory.Point> pts = PriceHistory.parse(sb.toString());
        assertEquals(380, pts.size());
        assertTrue(pts.get(0).date().isBefore(pts.get(pts.size() - 1).date()), "oldest first");
        assertEquals(3, PriceHistory.parse("{\"values\":[{\"datetime\":\"2026-01-03\",\"close\":\"1\"},"
                + "{\"datetime\":\"2026-01-02\",\"close\":\"1\"},{\"datetime\":\"2026-01-01\",\"close\":\"1\"}]}").size(),
                "a short answer is not padded");
    }
}
