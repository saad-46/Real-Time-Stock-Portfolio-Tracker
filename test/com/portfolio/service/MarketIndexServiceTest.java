package com.portfolio.service;

import com.portfolio.model.Stock;
import com.portfolio.service.MarketIndexService.IndexDef;
import com.portfolio.service.MarketIndexService.Problem;
import com.portfolio.service.MarketIndexService.Provider;
import com.portfolio.service.MarketIndexService.Snapshot;
import com.portfolio.service.MarketIndexService.State;
import com.portfolio.service.PriceUnavailableException.Reason;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Index cards from fake providers: no network, no keys. The numbers are test fixtures, not market data. */
class MarketIndexServiceTest {
    private static final IndexDef A = new IndexDef("a", "Alpha", Map.of("p1", "AAA", "p2", "AA2"));
    private static final IndexDef B = new IndexDef("b", "Beta", Map.of("p1", "BBB", "p2", "BB2"));

    /** A scripted provider: per-symbol outcome (Stock, null, or RuntimeException), call counter. */
    private static final class Fake implements Provider {
        final String id;
        final boolean configured;
        final Map<String, Object> quotes;
        final String history;
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger histCalls = new AtomicInteger();

        Fake(String id, boolean configured, Map<String, Object> quotes, String history) {
            this.id = id;
            this.configured = configured;
            this.quotes = quotes;
            this.history = history;
        }

        @Override public String id() { return id; }
        @Override public String name() { return "Prov-" + id; }
        @Override public boolean configured() { return configured; }

        @Override public Stock quote(String symbol) {
            calls.incrementAndGet();
            Object o = quotes.get(symbol);
            if (o instanceof RuntimeException e) throw e;
            return (Stock) o;
        }

        @Override public String history(String symbol, int days) {
            histCalls.incrementAndGet();
            if (history == null) throw new PriceUnavailableException(Reason.UNSUPPORTED, "no history");
            return history;
        }
    }

    private static String history(int days) {
        StringBuilder sb = new StringBuilder("{\"values\":[");
        for (int i = days; i >= 1; i--) {
            sb.append("{\"datetime\":\"").append(java.time.LocalDate.of(2026, 3, 1).minusDays(i)).append("\",\"close\":\"").append(100 + i).append("\"}");
            if (i > 1) sb.append(',');
        }
        return sb.append("]}").toString();
    }

    private static Stock stock(String s, double price, double change) {
        Stock st = new Stock(s, s, price);
        st.setChangePercent(change);
        return st;
    }

    private static PriceUnavailableException ex(Reason r) {
        return new PriceUnavailableException(r, "secret-detail " + r);
    }

    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-03-01T10:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static MarketIndexService svc(TestClock c, Provider... ps) {
        return new MarketIndexService(List.of(ps), List.of(A, B), Duration.ofMinutes(5), Duration.ofMinutes(15), c);
    }

    @Test
    void freshIndexCarriesTheProviderValueChangeAndSourceAndHistoryComesOnDemand() {
        TestClock c = new TestClock();
        Fake p = new Fake("p1", true, Map.of("AAA", stock("AAA", 1234.5, -0.75), "BBB", stock("BBB", 99.0, 1.5)), history(40));
        MarketIndexService s = svc(c, p);
        List<Snapshot> out = s.refresh(false);
        Snapshot a = out.get(0);
        assertEquals(State.FRESH, a.state());
        assertEquals(1234.5, a.value(), 0.0);
        assertEquals(-0.75, a.changePercent(), 0.0);
        assertEquals("Prov-p1", a.provider());
        assertEquals(0, a.history().size(), "a refresh asks for quotes only (it protects the providers' daily quota)");
        assertEquals(c.now, a.fetchedAt());
        Snapshot withHistory = s.history("a");
        assertEquals(40, withHistory.history().size());
        assertEquals(1234.5, withHistory.value(), 0.0, "the quote is kept");
        assertSame(withHistory, s.history("a"), "history is cached: no second request");
        assertNull(s.history("nope"));
        assertEquals(40, s.refresh(true).get(0).history().size(), "a refresh keeps the history it already has");
    }

    @Test
    void historyThatNoProviderCanGiveIsRecordedWithTheReasonAndNotRetriedEveryClick() {
        Fake p = new Fake("p1", true, Map.of("AAA", stock("AAA", 5, 0), "BBB", stock("BBB", 6, 0)), null);
        MarketIndexService s = svc(new TestClock(), p);
        s.refresh(true);
        Snapshot none = s.history("a");
        assertEquals(0, none.history().size());
        assertTrue(none.message().startsWith("No daily history"), none.message());
        assertEquals(5, none.value(), 0.0);
    }

    @Test
    void noKeyAtAllIsNotConfiguredAndMakesNoRequests() {
        Fake p = new Fake("p1", false, Map.of(), null);
        List<Snapshot> out = svc(new TestClock(), p).refresh(true);
        assertEquals(Problem.NOT_CONFIGURED, out.get(0).problem());
        assertEquals("API key not configured", out.get(0).message());
        assertEquals(0, p.calls.get());
        assertFalse(out.get(0).hasValue());
    }

    @Test
    void eachFailureKindIsDistinctAndSafe() {
        Object[][] cases = { { Reason.INVALID_KEY, Problem.INVALID_KEY, "Invalid API key" }, { Reason.RATE_LIMIT, Problem.RATE_LIMIT, "rate limit" },
                { Reason.NETWORK, Problem.NETWORK, "Network unavailable" }, { Reason.BAD_RESPONSE, Problem.BAD_RESPONSE, "unreadable" },
                { Reason.SYMBOL_NOT_FOUND, Problem.UNSUPPORTED, "does not support" } };
        for (Object[] k : cases) {
            Fake p = new Fake("p1", true, Map.of("AAA", ex((Reason) k[0]), "BBB", ex((Reason) k[0])), null);
            Snapshot s = svc(new TestClock(), p).refresh(true).get(0);
            assertEquals(State.UNAVAILABLE, s.state(), k[0].toString());
            assertEquals(k[1], s.problem(), k[0].toString());
            assertTrue(s.message().toLowerCase().contains(((String) k[2]).toLowerCase()), s.message());
            assertFalse(s.message().contains("secret-detail"), "provider exception text is not shown");
        }
    }

    @Test
    void anUnsupportedIndexFallsThroughToTheNextConfiguredProvider() {
        Fake rejects = new Fake("p1", true, java.util.Collections.singletonMap("AAA", null), null);
        Fake serves = new Fake("p2", true, Map.of("AA2", stock("AA2", 50, 0.1), "BB2", stock("BB2", 60, 0.2)), history(5));
        List<Snapshot> out = svc(new TestClock(), rejects, serves).refresh(true);
        assertEquals("Prov-p2", out.get(0).provider());
        assertEquals(50, out.get(0).value(), 0.0);
        // the unconfigured provider is never asked
        Fake off = new Fake("p1", false, Map.of(), null);
        svc(new TestClock(), off, serves).refresh(true);
        assertEquals(0, off.calls.get());
    }

    @Test
    void oneIndexFailingNeverAffectsTheOther() {
        Fake p = new Fake("p1", true, Map.of("AAA", ex(Reason.NETWORK), "BBB", stock("BBB", 5, 0.5)), null);
        List<Snapshot> out = svc(new TestClock(), p).refresh(true);
        assertEquals(State.UNAVAILABLE, out.get(0).state());
        assertEquals(State.FRESH, out.get(1).state());
        assertEquals(0, out.get(1).history().size(), "no history just means no sparkline");
    }

    @Test
    void zeroPriceOrRuntimeFailureIsNeverShownAsAValue() {
        Fake p = new Fake("p1", true, Map.of("AAA", stock("AAA", 0.0, 0.0), "BBB", new IllegalStateException("boom")), null);
        List<Snapshot> out = svc(new TestClock(), p).refresh(true);
        assertFalse(out.get(0).hasValue() || out.get(1).hasValue());
        assertEquals(Problem.BAD_RESPONSE, out.get(0).problem());
        assertFalse(out.get(1).message().contains("boom"));
    }

    @Test
    void cacheAvoidsDuplicateRequestsUntilItExpiresOrIsForced() {
        TestClock c = new TestClock();
        Fake p = new Fake("p1", true, Map.of("AAA", stock("AAA", 1, 0), "BBB", stock("BBB", 2, 0)), null);
        MarketIndexService s = svc(c, p);
        s.refresh(false);
        assertEquals(2, p.calls.get());
        s.refresh(false);
        assertEquals(2, p.calls.get(), "served from the cache");
        s.refresh(true);
        assertEquals(4, p.calls.get(), "forced refresh asks again");
        c.now = c.now.plus(Duration.ofMinutes(6));
        s.refresh(false);
        assertEquals(6, p.calls.get(), "cache expired");
    }

    @Test
    void rateLimitBacksOffAndRecoversAfterwards() {
        TestClock c = new TestClock();
        java.util.Map<String, Object> quotes = new java.util.concurrent.ConcurrentHashMap<>();
        quotes.put("AAA", ex(Reason.RATE_LIMIT));
        quotes.put("BBB", ex(Reason.RATE_LIMIT));
        Fake p = new Fake("p1", true, quotes, null);
        MarketIndexService s = svc(c, p);
        s.refresh(true);
        int after = p.calls.get();
        List<Snapshot> again = s.refresh(true);
        assertEquals(after, p.calls.get(), "no request while the provider is rate limited, even when forced");
        assertEquals(Problem.RATE_LIMIT, again.get(0).problem());
        quotes.put("AAA", stock("AAA", 7, 0.1));
        quotes.put("BBB", stock("BBB", 8, 0.2));
        c.now = c.now.plus(Duration.ofMinutes(3));
        List<Snapshot> recovered = s.refresh(true);
        assertEquals(State.FRESH, recovered.get(0).state(), "recovers once the back-off ended");
        assertEquals(7, recovered.get(0).value(), 0.0);
    }

    @Test
    void aFailedRefreshKeepsTheLastValueButLabelsItStaleWithTheReason() {
        TestClock c = new TestClock();
        java.util.Map<String, Object> quotes = new java.util.concurrent.ConcurrentHashMap<>();
        quotes.put("AAA", stock("AAA", 10, 1));
        quotes.put("BBB", stock("BBB", 20, 2));
        Fake p = new Fake("p1", true, quotes, null);
        MarketIndexService s = svc(c, p);
        assertEquals(State.FRESH, s.refresh(true).get(0).state());
        quotes.put("AAA", ex(Reason.NETWORK));
        Snapshot kept = s.refresh(true).get(0);
        assertEquals(State.STALE, kept.state());
        assertEquals(10, kept.value(), 0.0);
        assertTrue(kept.message().contains("Refresh failed"));
        assertEquals(Problem.NETWORK, kept.problem());
    }

    @Test
    void oldFreshSnapshotsBecomeStaleAndNeverLive() {
        TestClock c = new TestClock();
        Fake p = new Fake("p1", true, Map.of("AAA", stock("AAA", 10, 1), "BBB", stock("BBB", 20, 2)), null);
        MarketIndexService s = svc(c, p);
        s.refresh(true);
        c.now = c.now.plus(Duration.ofMinutes(20));
        List<Snapshot> now = s.current();
        assertEquals(State.STALE, now.get(0).state());
        assertEquals(10, now.get(0).value(), 0.0);
        assertEquals(0, p.calls.get() - 2, "reading current() makes no request");
    }

    @Test
    void defaultInstrumentsAreTwoIndicesAndSixIndianCompaniesWithProviderSpecificSymbolsAndNoValues() {
        List<String> names = MarketIndexService.DEFAULT_INDICES.stream().map(IndexDef::name).toList();
        assertEquals(List.of("NIFTY 50", "SENSEX", "Reliance Industries", "HDFC Bank", "ICICI Bank", "Tata Consultancy Services", "Infosys", "State Bank of India"), names);
        assertEquals(List.of("NIFTY", "SENSEX", "RELIANCE", "HDFCBANK", "ICICIBANK", "TCS", "INFY", "SBIN"),
                MarketIndexService.DEFAULT_INDICES.stream().map(IndexDef::ticker).toList());
        IndexDef infosys = MarketIndexService.DEFAULT_INDICES.get(6);
        assertEquals("INFY:NSE", infosys.symbols().get("twelvedata"));
        assertEquals("INFY.NS", infosys.symbols().get("finnhub"));
        assertEquals("INFY.BSE", infosys.symbols().get("alphavantage"));
        IndexDef sensex = MarketIndexService.DEFAULT_INDICES.get(1);
        assertNull(sensex.symbols().get("alphavantage"), "no index symbol is guessed for Alpha Vantage (only an ETF exists there)");
        assertFalse(Snapshot.loading(MarketIndexService.DEFAULT_INDICES.get(0)).hasValue());
    }

    @Test
    void lastQuotesSurviveARestartThroughTheCacheFileAndNeverContainKeys(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path file = tmp.resolve("cache.properties");
        TestClock c = new TestClock();
        Fake p = new Fake("p1", true, Map.of("AAA", stock("AAA", 1234.5, -0.75), "BBB", stock("BBB", 99.0, 1.5)), null);
        svc(c, p).withCacheFile(file).refresh(true);
        String text = java.nio.file.Files.readString(file);
        assertTrue(text.contains("a.value=1234.5") && text.contains("b.value=99.0"), text);
        assertFalse(text.toLowerCase().contains("key"), "prices only");

        Fake offline = new Fake("p1", true, Map.of("AAA", ex(Reason.NETWORK), "BBB", ex(Reason.NETWORK)), null);
        MarketIndexService restarted = svc(c, offline).withCacheFile(file);
        List<Snapshot> shown = restarted.current();
        assertEquals(1234.5, shown.get(0).value(), 0.0);
        assertEquals("Prov-p1", shown.get(0).provider());
        assertEquals(State.FRESH, shown.get(0).state());
        assertEquals(0, offline.calls.get());
        assertEquals(0, restarted.refresh(false).get(0).history().size());
        assertEquals(0, offline.calls.get(), "a fresh cache entry is used without asking the provider again");
        // an old cached value is shown as stale, never as live
        c.now = c.now.plus(Duration.ofMinutes(30));
        assertEquals(State.STALE, restarted.current().get(0).state());
        // a damaged file is ignored
        java.nio.file.Files.writeString(file, "a.value=banana\na.fetched=never\n");
        assertEquals(State.LOADING, svc(c, offline).withCacheFile(file).current().get(0).state());
    }

    @Test
    void concurrentRefreshIsNotStartedTwice() throws Exception {
        java.util.concurrent.CountDownLatch inside = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        Provider slow = new Provider() {
            @Override public String id() { return "p1"; }
            @Override public String name() { return "Slow"; }
            @Override public boolean configured() { return true; }
            @Override public Stock quote(String symbol) {
                calls.incrementAndGet();
                inside.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return stock(symbol, 1, 0);
            }
            @Override public String history(String symbol, int days) { throw ex(Reason.UNSUPPORTED); }
        };
        MarketIndexService s = svc(new TestClock(), slow);
        List<Snapshot> first = new ArrayList<>();
        Thread t = new Thread(() -> first.addAll(s.refresh(true)));
        t.start();
        inside.await();
        List<Snapshot> second = s.refresh(true); // while the first is running
        assertEquals(Snapshot.loading(A).state(), second.get(0).state(), "no value yet, and no second round of requests");
        release.countDown();
        t.join();
        assertEquals(2, calls.get(), "one request per index, not two rounds");
        assertEquals(2, first.size());
    }

    @Test
    void whenEveryProviderFailsTheCardNamesEachProvidersReasonSoOneNeverHidesAnother() {
        Fake bad = new Fake("p1", true, Map.of("AAA", ex(Reason.INVALID_KEY), "BBB", ex(Reason.INVALID_KEY)), null);
        Fake limited = new Fake("p2", true, Map.of("AA2", ex(Reason.RATE_LIMIT), "BB2", ex(Reason.RATE_LIMIT)), null);
        Snapshot s = svc(new TestClock(), bad, limited).refresh(true).get(0);
        assertEquals(Problem.INVALID_KEY, s.problem(), "the most actionable problem decides the state");
        assertTrue(s.message().contains("Prov-p1: invalid API key"), s.message());
        assertTrue(s.message().contains("Prov-p2: rate limit reached"), s.message());
    }

    @Test
    void aProviderWithAMinimumIntervalIsNeverAskedFasterThanThat() {
        Provider slow = new Provider() {
            final List<Long> at = new ArrayList<>();
            @Override public String id() { return "p1"; }
            @Override public String name() { return "Paced"; }
            @Override public boolean configured() { return true; }
            @Override public long minIntervalMillis() { return 120; }
            @Override public Stock quote(String symbol) { at.add(System.nanoTime()); return stock(symbol, 1, 0); }
            @Override public String history(String symbol, int days) {
                at.add(System.nanoTime());
                if (at.size() == 4) {
                    long gapMs = (at.get(3) - at.get(0)) / 1_000_000L;
                    assertTrue(gapMs >= 3 * 110, "four requests need at least three intervals, took " + gapMs + " ms");
                }
                throw ex(Reason.UNSUPPORTED);
            }
        };
        List<Snapshot> out = svc(new TestClock(), slow).refresh(true);
        assertEquals(2, out.size());
        assertEquals(State.FRESH, out.get(1).state());
    }

    @Test
    void aDailyQuotaPausesTheProviderForHoursAndTheNextRunRemembersIt(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) {
        java.nio.file.Path file = tmp.resolve("cache.properties");
        TestClock c = new TestClock();
        PriceUnavailableException daily = new PriceUnavailableException(Reason.RATE_LIMIT, "The provider's daily quota was reached");
        java.util.Map<String, Object> quotes = new java.util.concurrent.ConcurrentHashMap<>();
        quotes.put("AAA", daily);
        quotes.put("BBB", daily);
        Fake p = new Fake("p1", true, quotes, null);
        MarketIndexService s = svc(c, p).withCacheFile(file);
        Snapshot first = s.refresh(true).get(0);
        assertEquals(Problem.QUOTA, first.problem());
        assertTrue(first.message().contains("daily quota used up"), first.message());
        int asked = p.calls.get();
        assertEquals(Problem.QUOTA, s.refresh(true).get(0).problem());
        c.now = c.now.plus(Duration.ofHours(3));
        s.refresh(true);
        assertEquals(asked, p.calls.get(), "no request for hours, even when forced: it would only waste attempts");

        // a restart keeps the pause (the quota does not reset because the program restarted)
        Fake again = new Fake("p1", true, Map.of("AAA", stock("AAA", 1, 0), "BBB", stock("BBB", 2, 0)), null);
        MarketIndexService restarted = svc(c, again).withCacheFile(file);
        assertEquals(Problem.QUOTA, restarted.refresh(true).get(0).problem());
        assertEquals(0, again.calls.get());
        c.now = c.now.plus(Duration.ofHours(4));
        assertEquals(State.FRESH, restarted.refresh(true).get(0).state(), "after the pause it asks again");
    }

    @Test
    void historySurvivesARestartIsReusedWhileRecentAndKeptWhenARefreshFails(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) {
        java.nio.file.Path file = tmp.resolve("cache.properties");
        TestClock c = new TestClock();
        Fake p = new Fake("p1", true, Map.of("AAA", stock("AAA", 10, 1), "BBB", stock("BBB", 20, 2)), history(30));
        MarketIndexService s = svc(c, p).withCacheFile(file);
        s.refresh(true);
        assertEquals(30, s.history("a").history().size());
        assertEquals(1, p.histCalls.get());

        Fake offline = new Fake("p1", true, Map.of("AAA", ex(Reason.NETWORK), "BBB", ex(Reason.NETWORK)), null);
        MarketIndexService restarted = svc(c, offline).withCacheFile(file);
        assertEquals(30, restarted.current().get(0).history().size(), "the series is restored");
        assertEquals(30, restarted.history("a").history().size());
        assertEquals(0, offline.histCalls.get(), "a recent stored series is used without asking the provider again");

        c.now = c.now.plus(Duration.ofHours(13));
        assertEquals(30, restarted.history("a").history().size(), "an old series is kept when the refresh fails");
        assertEquals(1, offline.histCalls.get(), "it did try to refresh once");
    }
}
