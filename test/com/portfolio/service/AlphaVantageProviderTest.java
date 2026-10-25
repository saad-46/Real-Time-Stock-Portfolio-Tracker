package com.portfolio.service;

import com.portfolio.database.DatabaseManager;
import com.portfolio.model.PortfolioItem;
import com.portfolio.model.Stock;
import com.portfolio.service.PriceUnavailableException.Reason;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** One local fake-server case per distinct provider response, plus request counts (no retry storms). */
class AlphaVantageProviderTest {
    private record Reply(int status, String body) { }

    private HttpServer server;
    private final Queue<Reply> replies = new ArrayDeque<>();
    private final AtomicInteger requests = new AtomicInteger();
    @TempDir Path tmp;

    @BeforeEach
    void start() throws Exception {
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("p.db").toString());
        com.portfolio.TestWallet.fund();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/query", ex -> {
            requests.incrementAndGet();
            Reply r;
            synchronized (replies) {
                r = replies.size() > 1 ? replies.poll() : replies.peek(); // last reply repeats
            }
            byte[] b = r.body().getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(r.status(), b.length);
            try (var out = ex.getResponseBody()) {
                out.write(b);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    private void reply(int status, String body) {
        synchronized (replies) {
            replies.add(new Reply(status, body));
        }
    }

    private AlphaVantageService svc() {
        return new AlphaVantageService("test-key", "http://127.0.0.1:" + server.getAddress().getPort() + "/query", Duration.ofSeconds(5));
    }

    private Reason reasonOf(String sym) {
        return assertThrows(PriceUnavailableException.class, () -> svc().getStock(sym)).getReason();
    }

    private static final String OK = "{\"Global Quote\":{\"05. price\":\"42.50\"}}";

    @Test
    void invalidKeyErrorMessageIsInvalidKeyNotSymbolNotFound() {
        reply(200, "{\"Error Message\":\"the parameter apikey is invalid or missing. Please claim your free API key\"}");
        assertEquals(Reason.INVALID_KEY, reasonOf("AAPL"));
        assertEquals(1, requests.get());
    }

    @Test
    void unknownSymbolErrorMessageIsNotFound() {
        reply(200, "{\"Error Message\":\"Invalid API call. Please retry or visit the documentation\"}");
        assertNull(svc().getStock("NOPE"));
        assertEquals(1, requests.get());
    }

    @Test
    void quotaInformationIsRateLimitAndNotRetried() {
        reply(200, "{\"Information\":\"Thank you for using Alpha Vantage! Our standard API rate limit is 25 requests per day.\"}");
        assertEquals(Reason.RATE_LIMIT, reasonOf("AAPL"));
        assertEquals(1, requests.get());
    }

    @Test
    void aDailyQuotaIsWordedAsSuch() {
        reply(200, "{\"Information\":\"Thank you for using Alpha Vantage! Our standard API rate limit is 25 requests per day.\"}");
        PriceUnavailableException daily = assertThrows(PriceUnavailableException.class, () -> svc().getStock("AAPL"));
        assertEquals(Reason.RATE_LIMIT, daily.getReason());
        assertTrue(daily.getMessage().contains("daily quota"), daily.getMessage());
    }

    @Test
    void aBurstLimitIsNotCalledADailyQuota() {
        reply(200, "{\"Note\":\"call frequency is 5 calls per minute\"}");
        PriceUnavailableException burst = assertThrows(PriceUnavailableException.class, () -> svc().getStock("AAPL"));
        assertFalse(burst.getMessage().contains("daily quota"), burst.getMessage());
    }

    @Test
    void noteIsRateLimitAndNotRetried() {
        reply(200, "{\"Note\":\"call frequency is 5 calls per minute\"}");
        assertEquals(Reason.RATE_LIMIT, reasonOf("AAPL"));
        assertEquals(1, requests.get());
    }

    @Test
    void demoKeyInformationIsInvalidKey() {
        reply(200, "{\"Information\":\"The **demo** API key is for demo purposes only. Please claim your free API key.\"}");
        assertEquals(Reason.INVALID_KEY, reasonOf("AAPL"));
    }

    @Test
    void unknownInformationIsProviderMessage() {
        reply(200, "{\"Information\":\"Something new\"}");
        assertEquals(Reason.PROVIDER_MESSAGE, reasonOf("AAPL"));
    }

    @Test
    void http401403AreInvalidKeyAndNotRetried() {
        reply(401, "{}");
        assertEquals(Reason.INVALID_KEY, reasonOf("AAPL"));
        assertEquals(1, requests.get());
    }

    @Test
    void http429IsRateLimitAndNotRetried() {
        reply(429, "{}");
        assertEquals(Reason.RATE_LIMIT, reasonOf("AAPL"));
        assertEquals(1, requests.get());
    }

    @Test
    void http404IsHttpErrorAndNotRetried() {
        reply(404, "{}");
        assertEquals(Reason.HTTP_ERROR, reasonOf("AAPL"));
        assertEquals(1, requests.get());
    }

    @Test
    void transient503ThenSuccessRetriesOnce() {
        reply(503, "{}");
        reply(200, OK);
        Stock s = svc().getStock("AAPL");
        assertEquals(42.5, s.getCurrentPrice(), 0.0);
        assertEquals(2, requests.get());
    }

    @Test
    void persistent500StopsAfterMaxAttempts() {
        reply(500, "{}");
        assertEquals(Reason.HTTP_ERROR, reasonOf("AAPL"));
        assertEquals(AlphaVantageService.MAX_ATTEMPTS, requests.get());
    }

    @Test
    void malformedBodyIsBadResponseAndNotRetried() {
        reply(200, "<html>");
        assertEquals(Reason.BAD_RESPONSE, reasonOf("AAPL"));
        assertEquals(1, requests.get());
    }

    @Test
    void missingKeyNeverContactsProvider() {
        PriceUnavailableException e = assertThrows(PriceUnavailableException.class,
                () -> new AlphaVantageService(" ", "http://127.0.0.1:" + server.getAddress().getPort() + "/query", Duration.ofSeconds(1)).getStock("AAPL"));
        assertEquals(Reason.NO_KEY, e.getReason());
        assertEquals(0, requests.get());
    }

    @Test
    void keyNeverAppearsInErrorMessages() {
        reply(200, "{\"Note\":\"x\"}");
        assertFalse(assertThrows(PriceUnavailableException.class, () -> svc().getStock("AAPL")).getMessage().contains("test-key"));
        replies.clear();
        reply(500, "{}");
        assertFalse(assertThrows(PriceUnavailableException.class, () -> svc().getStock("AAPL")).getMessage().contains("test-key"));
    }

    // ---- batch behaviour in PortfolioService ----

    private PortfolioService portfolioWith(int holdings) {
        PortfolioService p = new PortfolioService(svc());
        for (int i = 0; i < holdings; i++) {
            p.buyStock(new PortfolioItem("S" + i, "S" + i, 1, 10.0, 10.0));
        }
        return p;
    }

    @Test
    void rateLimitStopsTheBatchAfterOneRequestAndCountsAllAsFailed() {
        reply(200, "{\"Note\":\"limit\"}");
        PortfolioService p = portfolioWith(5);
        assertEquals(5, p.updateAllPrices());
        assertEquals(1, requests.get()); // not 5, and no retries
        assertTrue(p.getLastRefreshProblem().toLowerCase().contains("limit"));
        for (PortfolioItem i : p.getAllPortfolioItems()) {
            assertNull(i.getPriceUpdatedAt());
            assertEquals(10.0, i.getCurrentPrice(), 0.0);
        }
    }

    @Test
    void invalidKeyStopsTheBatch() {
        reply(200, "{\"Error Message\":\"the parameter apikey is invalid or missing\"}");
        PortfolioService p = portfolioWith(3);
        assertEquals(3, p.updateAllPrices());
        assertEquals(1, requests.get());
        assertTrue(p.getLastRefreshProblem().contains("API key"));
    }

    @Test
    void notFoundAndTransientFailuresDoNotStopTheBatch() {
        reply(200, "{\"Global Quote\":{}}");     // S0 not found
        reply(200, OK);                          // S1 ok (last reply repeats for the rest)
        PortfolioService p = portfolioWith(3);
        assertEquals(1, p.updateAllPrices());
        assertEquals(3, requests.get());
        assertTrue(p.getLastRefreshProblem().contains("not found"));
        long fresh = p.getAllPortfolioItems().stream().filter(i -> i.getPriceUpdatedAt() != null).count();
        assertEquals(2, fresh);
    }
}
