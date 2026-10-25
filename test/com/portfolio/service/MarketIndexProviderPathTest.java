package com.portfolio.service;

import com.portfolio.service.MarketIndexService.IndexDef;
import com.portfolio.service.MarketIndexService.Problem;
import com.portfolio.service.MarketIndexService.Snapshot;
import com.portfolio.service.MarketIndexService.State;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** The real Twelve Data and Finnhub clients against a local fake server: the whole index path, no network, no keys. */
class MarketIndexProviderPathTest {
    private static final IndexDef SPX = new IndexDef("sp500", "S&P 500", Map.of("twelvedata", "SPX", "finnhub", "^GSPC"));

    HttpServer server;
    String base;
    volatile int status = 200;
    volatile String quoteBody = "{}";
    volatile String historyBody = "{}";
    volatile String finnhubBody = "{\"c\":0,\"d\":null,\"dp\":null}";
    final List<String> requests = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String uri = ex.getRequestURI().toString();
            requests.add(uri);
            String body = uri.startsWith("/time_series") ? historyBody : uri.startsWith("/quote?symbol=%5EGSPC") ? finnhubBody : quoteBody;
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private MarketIndexService service(boolean td, boolean fh) {
        return new MarketIndexService(List.of(
                MarketIndexService.twelveData(new TwelveDataService(base, () -> "test-key-1"), () -> td),
                MarketIndexService.finnhub(new FinnhubService(base, () -> "test-key-2"), () -> fh)),
                List.of(SPX), Duration.ofMinutes(5), Duration.ofMinutes(15), Clock.systemUTC());
    }

    private static String series() {
        StringBuilder sb = new StringBuilder("{\"values\":[");
        for (int i = 60; i >= 1; i--) {
            sb.append("{\"datetime\":\"").append(java.time.LocalDate.of(2026, 3, 1).minusDays(i)).append("\",\"close\":\"").append(5000 + i).append(".5\"}");
            if (i > 1) sb.append(',');
        }
        return sb.append("]}").toString();
    }

    @Test
    void aSupportedIndexIsReadThroughTheRealClientWithHistory() {
        quoteBody = "{\"symbol\":\"SPX\",\"close\":\"5400.50\",\"percent_change\":\"-0.31\",\"high\":\"5420\",\"low\":\"5380\"}";
        historyBody = series();
        MarketIndexService svc = service(true, false);
        Snapshot s = svc.refresh(true).get(0);
        assertEquals(State.FRESH, s.state());
        assertEquals(5400.50, s.value(), 0.0);
        assertEquals(-0.31, s.changePercent(), 1e-9);
        assertEquals("Twelve Data", s.provider());
        assertEquals(60, svc.history("sp500").history().size());
        assertTrue(requests.stream().anyMatch(r -> r.startsWith("/quote?symbol=SPX")), "asks for the provider's own symbol: " + requests);
    }

    @Test
    void anInvalidKeyIsReportedAsSuchWithoutRevealingTheKey() {
        status = 401;
        Snapshot s = service(true, false).refresh(true).get(0);
        assertEquals(Problem.INVALID_KEY, s.problem());
        assertTrue(s.message().contains("invalid API key"), s.message());
        assertFalse(s.message().contains("test-key-1"));
    }

    @Test
    void aRateLimitIsReportedThenNotRetriedUntilTheBackoffEnds() {
        status = 429;
        MarketIndexService svc = service(true, false);
        assertEquals(Problem.RATE_LIMIT, svc.refresh(true).get(0).problem());
        int seen = requests.size();
        assertEquals(Problem.RATE_LIMIT, svc.refresh(true).get(0).problem());
        assertEquals(seen, requests.size(), "no second request while paused");
    }

    @Test
    void aPlanLimitedSymbolIsUnsupportedAndFallsThroughToTheNextProvider() {
        quoteBody = "{\"code\":403,\"message\":\"This symbol is available starting with Grow\",\"status\":\"error\"}";
        finnhubBody = "{\"c\":5401.25,\"d\":-10,\"dp\":-0.18,\"h\":5410,\"l\":5390,\"o\":5395,\"pc\":5411}";
        Snapshot s = service(true, true).refresh(true).get(0);
        assertEquals("Finnhub", s.provider(), "the next configured provider answered");
        assertEquals(5401.25, s.value(), 0.0);

        Snapshot only = service(true, false).refresh(true).get(0);
        assertEquals(Problem.UNSUPPORTED, only.problem());
        assertTrue(only.message().contains("does not support"), only.message());
    }

    @Test
    void aMalformedAnswerIsNeverAValue() {
        quoteBody = "<html>maintenance</html>";
        Snapshot s = service(true, false).refresh(true).get(0);
        assertFalse(s.hasValue());
        assertTrue(s.problem() == Problem.BAD_RESPONSE || s.problem() == Problem.PROVIDER_ERROR, s.problem().toString());
    }

    @Test
    void anUnreachableProviderIsANetworkProblem() {
        server.stop(0);
        Snapshot s = service(true, false).refresh(true).get(0);
        assertEquals(Problem.NETWORK, s.problem());
        assertEquals("Network unavailable", s.message());
    }

    @Test
    void withNoKeyConfiguredNothingIsRequested() {
        Snapshot s = service(false, false).refresh(true).get(0);
        assertEquals(Problem.NOT_CONFIGURED, s.problem());
        assertTrue(requests.isEmpty());
    }

    @Test
    void aValidKeyWithoutAccessToTheSymbolIsUnsupportedNotInvalid() {
        // Finnhub answers 403 "You don't have access to this resource" for a symbol outside the plan, and 401 for a bad key
        status = 403;
        quoteBody = "{\"error\":\"You don't have access to this resource.\"}";
        finnhubBody = quoteBody;
        Snapshot s = service(false, true).refresh(true).get(0);
        assertEquals(Problem.UNSUPPORTED, s.problem(), s.message());
        assertFalse(s.message().toLowerCase().contains("invalid"), s.message());
        status = 401;
        assertEquals(Problem.INVALID_KEY, service(false, true).refresh(true).get(0).problem());
    }

    @Test
    void anExchangeQualifiedTwelveDataSymbolIsSentAsSymbolPlusExchange() {
        quoteBody = "{\"symbol\":\"RELIANCE\",\"close\":\"2950.40\",\"percent_change\":\"0.42\",\"currency\":\"INR\"}";
        historyBody = series();
        IndexDef reliance = new IndexDef("reliance", "Reliance Industries", Map.of("twelvedata", "RELIANCE:NSE"));
        MarketIndexService svc = new MarketIndexService(List.of(MarketIndexService.twelveData(new TwelveDataService(base, () -> "test-key-1"), () -> true)),
                List.of(reliance), Duration.ofMinutes(5), Duration.ofMinutes(15), Clock.systemUTC());
        Snapshot s = svc.refresh(true).get(0);
        svc.history("reliance");
        assertEquals(State.FRESH, s.state());
        assertEquals("INR", s.currency(), "the provider's stated currency is carried through");
        assertTrue(requests.stream().anyMatch(r -> r.startsWith("/quote?symbol=RELIANCE&exchange=NSE")), requests.toString());
        assertTrue(requests.stream().anyMatch(r -> r.startsWith("/time_series?symbol=RELIANCE&exchange=NSE")), requests.toString());
        assertEquals("symbol=TCS&exchange=NSE", TwelveDataService.symbolParams("TCS:NSE"));
        assertEquals("symbol=AAPL", TwelveDataService.symbolParams("AAPL"));
    }

    @Test
    void twelveDataNotFoundIsEitherAPlanLimitOrAnUnknownSymbolNeverAnInvalidKey() {
        status = 404;
        quoteBody = "{\"code\":404,\"message\":\"This symbol is available starting with the Grow or Venture plan.\",\"status\":\"error\"}";
        Snapshot plan = service(true, false).refresh(true).get(0);
        assertEquals(Problem.UNSUPPORTED, plan.problem(), plan.message());
        quoteBody = "{\"code\":404,\"message\":\"**symbol** or **figi** parameter is missing or invalid.\",\"status\":\"error\"}";
        MarketIndexService svc = service(true, false);
        Snapshot unknown = svc.refresh(true).get(0);
        assertEquals(Problem.UNSUPPORTED, unknown.problem(), unknown.message());
        assertFalse(unknown.message().toLowerCase().contains("invalid api key"));
    }
}
