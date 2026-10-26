package com.portfolio.service;

import com.portfolio.database.DatabaseManager;
import com.portfolio.database.PriceHistoryDAO;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Provider error bodies, failover reporting, cooldowns and concurrent access. Local servers and scripted providers only. */
class ProviderReliabilityTest {
    @TempDir Path tmp;
    private HttpServer server;
    private volatile int status = 200;
    private volatile String body = "{}";
    private final List<String> queries = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws Exception {
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("rel.db").toString());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            queries.add(ex.getRequestURI().toString());
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
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

    private TwelveDataService twelve() {
        return new TwelveDataService("http://127.0.0.1:" + server.getAddress().getPort(), () -> "test-key");
    }

    private AlphaVantageService alpha() {
        return new AlphaVantageService("test-key", "http://127.0.0.1:" + server.getAddress().getPort() + "/query", Duration.ofSeconds(5));
    }

    // ------------------------------------------------------------------ Twelve Data

    @Test
    void twelveDataErrorBodiesAreClassifiedEvenWithDifferentSpacingAndNeverBecomeAnEmptySeries() {
        body = "{\"code\":429,\"message\":\"You have run out of API credits\",\"status\":\"error\"}";
        assertEquals(Reason.RATE_LIMIT, assertThrows(PriceUnavailableException.class, () -> twelve().getHistoricalData("RELIANCE:BSE", 400)).getReason());
        body = "{ \"status\": \"error\",\n  \"code\": 401, \"message\": \"apikey incorrect\" }";
        assertEquals(Reason.INVALID_KEY, assertThrows(PriceUnavailableException.class, () -> twelve().getHistoricalData("RELIANCE:BSE", 400)).getReason());
        body = "{\"code\":403,\"message\":\"available starting with the Grow plan\",\"status\":\"error\"}";
        assertEquals(Reason.UNSUPPORTED, assertThrows(PriceUnavailableException.class, () -> twelve().getHistoricalData("RELIANCE:BSE", 400)).getReason());
        body = "{\"code\":404,\"message\":\"symbol not found\",\"status\":\"error\"}";
        assertEquals(Reason.SYMBOL_NOT_FOUND, assertThrows(PriceUnavailableException.class, () -> twelve().getHistoricalData("NOSUCH", 400)).getReason());
        body = "{\"code\":500,\"message\":\"internal\",\"status\":\"error\"}";
        assertEquals(Reason.PROVIDER_MESSAGE, assertThrows(PriceUnavailableException.class, () -> twelve().getHistoricalData("X", 400)).getReason());
    }

    @Test
    void twelveDataHttpStatusesAreClassified() {
        status = 429;
        assertEquals(Reason.RATE_LIMIT, assertThrows(PriceUnavailableException.class, () -> twelve().getHistoricalData("X", 400)).getReason());
        status = 401;
        assertEquals(Reason.INVALID_KEY, assertThrows(PriceUnavailableException.class, () -> twelve().getHistoricalData("X", 400)).getReason());
        status = 503;
        assertEquals(Reason.HTTP_ERROR, assertThrows(PriceUnavailableException.class, () -> twelve().getHistoricalData("X", 400)).getReason());
    }

    @Test
    void twelveDataGetsTheExchangeAsItsOwnParameterAndTheRequestedDepth() {
        body = "{\"values\":[{\"datetime\":\"2026-03-02\",\"close\":\"2900\"},{\"datetime\":\"2026-03-01\",\"close\":\"2890\"}]}";
        assertEquals(2, PriceHistory.parse(twelve().getHistoricalData("RELIANCE:BSE", 400)).size());
        String q = queries.get(queries.size() - 1);
        assertTrue(q.contains("symbol=RELIANCE") && q.contains("exchange=BSE") && q.contains("outputsize=400") && q.contains("interval=1day"), q);
    }

    // ------------------------------------------------------------------ Alpha Vantage history

    @Test
    void alphaVantageErrorsAndEmptyAnswersAreNotValidHistory() {
        body = "{\"Error Message\":\"Invalid API call.\"}";
        assertEquals(Reason.SYMBOL_NOT_FOUND, assertThrows(PriceUnavailableException.class, () -> alpha().getHistoricalData("NOSUCH.BSE", 100)).getReason());
        body = "{\"Information\":\"Our standard API rate limit is 25 requests per day.\"}";
        assertEquals(Reason.RATE_LIMIT, assertThrows(PriceUnavailableException.class, () -> alpha().getHistoricalData("X.BSE", 100)).getReason());
        body = "{}";
        String raw = alpha().getHistoricalData("X.BSE", 100);
        assertEquals(Reason.BAD_RESPONSE, assertThrows(PriceUnavailableException.class, () -> PriceHistory.parse(raw)).getReason(),
                "an answer with no series is an error, not an empty chart");
        body = "not json at all";
        assertThrows(PriceUnavailableException.class, () -> PriceHistory.parse(alpha().getHistoricalData("X.BSE", 100)));
    }

    // ------------------------------------------------------------------ provider chain reporting

    private static TwelveDataService failingTwelve(Reason reason, String message) {
        return new TwelveDataService() {
            @Override public String getHistoricalData(String symbol, int days) {
                throw new PriceUnavailableException(reason, message);
            }
        };
    }

    private static AlphaVantageService failingAlpha(Reason reason, String message) {
        return new AlphaVantageService("test-key") {
            @Override public String getHistoricalData(String symbol, int days) {
                throw new PriceUnavailableException(reason, message);
            }
        };
    }

    private static FinnhubService quietFinnhub() {
        return new FinnhubService();
    }

    @Test
    void whenEveryProviderFailsTheUserSeesTheMostActionableReasonAndEachProvidersMessage() {
        MultiSourceStockService chain = new MultiSourceStockService(quietFinnhub(),
                failingTwelve(Reason.UNSUPPORTED, "Twelve Data: this symbol is not included in the current plan"),
                failingAlpha(Reason.RATE_LIMIT, "The provider's daily quota was reached"));
        PriceUnavailableException e = assertThrows(PriceUnavailableException.class, () -> chain.getHistoricalData("RELIANCE.BSE", 400));
        assertEquals(Reason.RATE_LIMIT, e.getReason(), "a quota problem outranks 'not in plan'");
        assertTrue(e.getMessage().contains("Twelve Data") && e.getMessage().contains("Alpha Vantage") && e.getMessage().contains("daily quota"), e.getMessage());
    }

    @Test
    void aMissingKeyIsOnlyReportedWhenNothingElseWentWrong() {
        MultiSourceStockService chain = new MultiSourceStockService(quietFinnhub(),
                failingTwelve(Reason.NO_KEY, "No Twelve Data API key configured"),
                failingAlpha(Reason.SYMBOL_NOT_FOUND, "The provider has no data for that symbol"));
        PriceUnavailableException e = assertThrows(PriceUnavailableException.class, () -> chain.getHistoricalData("NOSUCH", 400));
        assertEquals(Reason.SYMBOL_NOT_FOUND, e.getReason());
        assertFalse(e.getMessage().contains("No Twelve Data API key"), e.getMessage());

        MultiSourceStockService nokeys = new MultiSourceStockService(quietFinnhub(),
                failingTwelve(Reason.NO_KEY, "No Twelve Data API key configured"), failingAlpha(Reason.NO_KEY, "No Alpha Vantage key"));
        assertEquals(Reason.NO_KEY, assertThrows(PriceUnavailableException.class, () -> nokeys.getHistoricalData("AAPL", 400)).getReason());
    }

    // ------------------------------------------------------------------ HistoryService: cooldowns and concurrency

    static final class Clock2 extends Clock {
        Instant now = Instant.parse("2026-03-10T10:00:00Z");

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }

        @Override public Clock withZone(ZoneId zone) { return this; }

        @Override public Instant instant() { return now; }
    }

    private static final String JSON = "{\"values\":[{\"datetime\":\"2026-03-02\",\"close\":\"101\"},{\"datetime\":\"2026-03-01\",\"close\":\"100\"}]}";

    private static final class Scripted implements StockPriceService {
        final AtomicInteger calls = new AtomicInteger();
        volatile RuntimeException failure;
        volatile CountDownLatch gate;

        @Override public Stock getStock(String symbol) { return null; }

        @Override public boolean isValidStock(String symbol) { return true; }

        @Override public String getHistoricalData(String symbol, int days) {
            calls.incrementAndGet();
            CountDownLatch g = gate;
            if (g != null) {
                try {
                    g.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failure != null) throw failure;
            return JSON;
        }
    }

    @Test
    void cooldownDependsOnWhyTheRequestFailed() {
        assertEquals(HistoryService.KEY_RETRY_AFTER, HistoryService.cooldown(new PriceUnavailableException(Reason.INVALID_KEY, "bad key")));
        assertEquals(HistoryService.KEY_RETRY_AFTER, HistoryService.cooldown(new PriceUnavailableException(Reason.NO_KEY, "no key")));
        assertEquals(HistoryService.QUOTA_RETRY_AFTER,
                HistoryService.cooldown(new PriceUnavailableException(Reason.RATE_LIMIT, "The provider's daily quota was reached")));
        assertEquals(HistoryService.RETRY_AFTER, HistoryService.cooldown(new PriceUnavailableException(Reason.RATE_LIMIT, "rate limit reached")));
        assertEquals(HistoryService.RETRY_AFTER, HistoryService.cooldown(new PriceUnavailableException(Reason.NETWORK, "timeout")));
    }

    @Test
    void aDailyQuotaFailureIsNotRetriedForHoursAndAKeyFailureIsRetriedSoon() {
        Scripted provider = new Scripted();
        Clock2 clock = new Clock2();
        HistoryService svc = new HistoryService(provider, new PriceHistoryDAO(), clock, "p");
        provider.failure = new PriceUnavailableException(Reason.RATE_LIMIT, "The provider's daily quota was reached");
        PriceUnavailableException first = assertThrows(PriceUnavailableException.class, () -> svc.get("TCS"));
        assertEquals(Reason.RATE_LIMIT, first.getReason());
        clock.now = clock.now.plus(Duration.ofHours(3));
        assertThrows(PriceUnavailableException.class, () -> svc.get("TCS"));
        assertEquals(1, provider.calls.get(), "no request for hours after a daily quota");
        clock.now = clock.now.plus(Duration.ofHours(4));
        provider.failure = null;
        assertEquals(2, svc.get("TCS").points().size());
        assertEquals(2, provider.calls.get());

        provider.failure = new PriceUnavailableException(Reason.INVALID_KEY, "bad key");
        assertThrows(PriceUnavailableException.class, () -> svc.get("ITC"));
        clock.now = clock.now.plus(Duration.ofMinutes(1));
        provider.failure = null;
        assertEquals(2, svc.get("ITC").points().size(), "after the user fixes the key, history comes back within a minute");
    }

    @Test
    void manyThreadsAskingForTheSameSymbolCauseOneProviderRequest() throws Exception {
        Scripted provider = new Scripted();
        provider.gate = new CountDownLatch(1);
        HistoryService svc = new HistoryService(provider, new PriceHistoryDAO(), new Clock2(), "p");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(pool.submit(() -> svc.get("INFY").points().size()));
            }
            Thread.sleep(300); // all eight are now waiting on the one request
            provider.gate.countDown();
            for (Future<Integer> f : results) {
                assertEquals(2, f.get(10, TimeUnit.SECONDS));
            }
            assertEquals(1, provider.calls.get(), "duplicate requests for one symbol are suppressed");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void differentSymbolsDoNotWaitForEachOther() throws Exception {
        Scripted provider = new Scripted();
        HistoryService svc = new HistoryService(provider, new PriceHistoryDAO(), new Clock2(), "p");
        CopyOnWriteArrayList<String> done = new CopyOnWriteArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            List<Future<?>> fs = new ArrayList<>();
            for (String s : List.of("AAA", "BBB", "CCC")) {
                fs.add(pool.submit(() -> {
                    svc.get(s);
                    done.add(s);
                }));
            }
            for (Future<?> f : fs) {
                f.get(15, TimeUnit.SECONDS);
            }
            assertEquals(3, done.size());
            assertEquals(3, provider.calls.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
