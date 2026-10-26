package com.portfolio.service;

import com.portfolio.database.DatabaseManager;
import com.portfolio.database.PriceHistoryDAO;
import com.portfolio.model.Stock;
import com.portfolio.model.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Disposable database and a scripted provider: no network. */
class HistoryServiceTest {
    @TempDir Path tmp;

    static final String JSON = "{\"values\":[{\"datetime\":\"2026-03-03\",\"close\":\"102.50\"},"
            + "{\"datetime\":\"2026-03-01\",\"close\":\"100.00\"},{\"datetime\":\"2026-03-02\",\"close\":\"101.25\"}]}";

    /** Scripted provider. */
    static final class Fake implements StockPriceService {
        final AtomicInteger calls = new AtomicInteger();
        volatile String answer = JSON;
        volatile RuntimeException failure;

        @Override public Stock getStock(String symbol) { return null; }

        @Override public boolean isValidStock(String symbol) { return true; }

        @Override public String getHistoricalData(String symbol) {
            calls.incrementAndGet();
            if (failure != null) throw failure;
            return answer;
        }
    }

    /** Settable clock. */
    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-03-10T10:00:00Z");

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }

        @Override public Clock withZone(ZoneId zone) { return this; }

        @Override public Instant instant() { return now; }
    }

    @BeforeEach
    void db() {
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("hist.db").toString());
    }

    @AfterEach
    void close() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    private static PriceUnavailableException quota() {
        return new PriceUnavailableException(PriceUnavailableException.Reason.RATE_LIMIT, "The provider's daily quota was reached");
    }

    @Test
    void parsesOrdersAndStoresTheProvidersCloses() {
        Fake provider = new Fake();
        HistoryService svc = new HistoryService(provider, new PriceHistoryDAO(), new TestClock(), "Test provider");
        HistoryService.Result r = svc.get("reliance");
        assertEquals(3, r.points().size());
        assertEquals(LocalDate.of(2026, 3, 1), r.points().get(0).date(), "oldest first although the provider sent newest first");
        assertEquals(100.00, r.points().get(0).close(), 1e-9);
        assertFalse(r.stale());
        assertEquals("Test provider", r.source());
        assertNotNull(svc.cached("RELIANCE"), "the series was saved for the next run");
    }

    @Test
    void aFreshStoredSeriesIsReusedWithoutAskingTheProviderAgain() {
        Fake provider = new Fake();
        TestClock clock = new TestClock();
        HistoryService svc = new HistoryService(provider, new PriceHistoryDAO(), clock, "p");
        svc.get("TCS");
        clock.now = clock.now.plus(Duration.ofHours(2));
        svc.get("TCS");
        // a brand-new service (a restart) also uses the stored series
        new HistoryService(provider, new PriceHistoryDAO(), clock, "p").get("TCS");
        assertEquals(1, provider.calls.get());
        clock.now = clock.now.plus(Duration.ofHours(13));
        svc.get("TCS");
        assertEquals(2, provider.calls.get(), "refreshed once it is older than 12 hours");
    }

    @Test
    void whenARefreshFailsTheStoredSeriesIsReturnedAndMarkedStale() {
        Fake provider = new Fake();
        TestClock clock = new TestClock();
        HistoryService svc = new HistoryService(provider, new PriceHistoryDAO(), clock, "p");
        svc.get("INFY");
        clock.now = clock.now.plus(Duration.ofHours(30));
        provider.failure = quota();
        HistoryService.Result r = svc.get("INFY");
        assertTrue(r.stale());
        assertEquals(3, r.points().size());
    }

    @Test
    void withNoStoredSeriesAFailureIsReportedAndNotRetriedForAWhile() {
        Fake provider = new Fake();
        provider.failure = new PriceUnavailableException(PriceUnavailableException.Reason.NETWORK, "Twelve Data network error: quota or timeout");
        TestClock clock = new TestClock();
        HistoryService svc = new HistoryService(provider, new PriceHistoryDAO(), clock, "p");
        PriceUnavailableException first = assertThrows(PriceUnavailableException.class, () -> svc.get("SBIN"));
        assertTrue(first.getMessage().contains("quota"));
        assertThrows(PriceUnavailableException.class, () -> svc.get("SBIN"));
        assertEquals(1, provider.calls.get(), "a failed symbol is not retried within 5 minutes");
        clock.now = clock.now.plus(Duration.ofMinutes(6));
        provider.failure = null;
        assertEquals(3, svc.get("SBIN").points().size());
        assertEquals(2, provider.calls.get());
    }

    @Test
    void garbageAndEmptyAnswersNeverBecomeAChart() {
        Fake provider = new Fake();
        TestClock clock = new TestClock();
        HistoryService svc = new HistoryService(provider, new PriceHistoryDAO(), clock, "p");
        provider.answer = "not json";
        assertThrows(PriceUnavailableException.class, () -> svc.get("ITC"));
        clock.now = clock.now.plus(Duration.ofMinutes(6));
        provider.answer = "{\"values\":[]}";
        assertThrows(PriceUnavailableException.class, () -> svc.get("ITC"));
        assertNull(svc.cached("ITC"), "nothing was stored");
    }

    // ---------------------------------------------------------------- portfolio reconstruction

    private static Transaction tx(String symbol, String type, int qty, double price, LocalDate day, Double realized) {
        return new Transaction(symbol, type, qty, price, realized, LocalDateTime.of(day, java.time.LocalTime.NOON));
    }

    private static List<PriceHistory.Point> closes(LocalDate start, double... v) {
        java.util.ArrayList<PriceHistory.Point> out = new java.util.ArrayList<>();
        for (int i = 0; i < v.length; i++) {
            out.add(new PriceHistory.Point(start.plusDays(i), v[i]));
        }
        return out;
    }

    @Test
    void investedCapitalFollowsTheLedgerAndValueFollowsRealCloses() {
        LocalDate d0 = LocalDate.of(2026, 3, 1);
        List<Transaction> ledger = List.of(
                tx("AAA", "BUY", 10, 100, d0, null),
                tx("AAA", "BUY", 10, 120, d0.plusDays(2), null),
                tx("AAA", "SELL", 5, 130, d0.plusDays(3), 5 * 130 - 5 * 110.0));
        Map<String, List<PriceHistory.Point>> data = Map.of("AAA", closes(d0, 100, 105, 120, 130));
        PortfolioHistory.Result r = PortfolioHistory.compute(ledger, data, d0.plusDays(3));
        assertEquals(4, r.days().size());
        PortfolioHistory.Day day0 = r.days().get(0);
        assertEquals(1000, day0.invested(), 1e-9);
        assertEquals(1000, day0.value(), 1e-9, "10 shares x close 100");
        PortfolioHistory.Day day1 = r.days().get(1);
        assertEquals(1000, day1.invested(), 1e-9);
        assertEquals(1050, day1.value(), 1e-9);
        PortfolioHistory.Day day2 = r.days().get(2);
        assertEquals(2200, day2.invested(), 1e-9, "1,000 + 10 shares at 120");
        assertEquals(20 * 120, day2.value(), 1e-9);
        PortfolioHistory.Day day3 = r.days().get(3);
        assertEquals(1650, day3.invested(), 1e-9, "selling 5 at 130 with 100 profit removes 550 of cost");
        assertEquals(15 * 130, day3.value(), 1e-9);
        assertTrue(r.symbolsWithoutHistory().isEmpty());
    }

    @Test
    void aSymbolWithoutHistoryIsValuedAtItsOwnTradePriceNeverAGuess() {
        LocalDate d0 = LocalDate.of(2026, 3, 1);
        List<Transaction> ledger = List.of(tx("BBB", "BUY", 4, 50, d0, null));
        PortfolioHistory.Result r = PortfolioHistory.compute(ledger, Map.of(), d0.plusDays(2));
        assertEquals(3, r.days().size());
        for (PortfolioHistory.Day d : r.days()) {
            assertEquals(200, d.value(), 1e-9, "the price of the trade is the only real observation");
            assertEquals(200, d.invested(), 1e-9);
        }
    }

    @Test
    void anEmptyLedgerGivesNoHistory() {
        assertTrue(PortfolioHistory.compute(List.of(), Map.of(), LocalDate.of(2026, 3, 1)).days().isEmpty());
    }

    @Test
    void closeOnOrBeforeADayIsUsedAndEarlierThanTheSeriesIsUnknown() {
        LocalDate d0 = LocalDate.of(2026, 3, 10);
        List<PriceHistory.Point> series = closes(d0, 10, 11);
        assertNull(PortfolioHistory.priceOn(series, d0.minusDays(1)));
        assertEquals(10, PortfolioHistory.priceOn(series, d0), 1e-9);
        assertEquals(11, PortfolioHistory.priceOn(series, d0.plusDays(30)), 1e-9);
        assertNull(PortfolioHistory.priceOn(null, d0));
    }
}
