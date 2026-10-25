package com.portfolio.service;

import com.portfolio.database.DatabaseManager;
import com.portfolio.model.PortfolioItem;
import com.portfolio.model.Stock;
import com.portfolio.service.TradeBlockedException.Reason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Freshness, currency and exchange-rate rules for trading. Rupee wallet, fake providers, fake exchange-rate source,
 * controllable clock, temporary database: no network, no real data.
 */
class TradePolicyTest {
    @TempDir Path tmp;

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-06-01T10:00:00Z");

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    static final class FakeProvider implements StockPriceService {
        final Map<String, Double> prices = new HashMap<>();
        final Map<String, String> currencies = new HashMap<>();
        RuntimeException failure;

        @Override
        public Stock getStock(String symbol) {
            if (failure != null) throw failure;
            Double p = prices.get(symbol);
            if (p == null) return null;
            Stock s = new Stock(symbol, symbol, p);
            s.setCurrency(currencies.get(symbol)); // null = the provider did not say
            return s;
        }

        @Override public boolean isValidStock(String symbol) { return getStock(symbol) != null; }
    }

    static final class FakeRates implements CurrencyService.RateSource {
        final Map<String, Double> rates = new HashMap<>();
        IOException failure;
        Instant stampOverride;   // if set, rates are reported as fetched at this time
        String pairOverride;     // if set, answer for the wrong currency pair
        int fetches;
        final MutableClock clock;

        FakeRates(MutableClock clock) { this.clock = clock; }

        @Override
        public CurrencyService.Rate fetch(String from, String to) throws IOException {
            fetches++;
            if (failure != null) throw failure;
            Double r = rates.get(from + ">" + to);
            if (r == null) throw new IOException("no rate for " + from + ">" + to);
            String f = pairOverride == null ? from : pairOverride;
            return new CurrencyService.Rate(f, to, r, stampOverride != null ? stampOverride : clock.instant(), "test-source");
        }
    }

    MutableClock clock = new MutableClock();
    FakeProvider provider = new FakeProvider();
    FakeRates rates = new FakeRates(clock);
    CurrencyService currency = new CurrencyService(rates, clock);

    @BeforeEach
    void setUp() {
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("p.db").toString());
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    /** A rupee wallet holding {@code funds}. */
    private PortfolioService inrWallet(double funds) {
        com.portfolio.TestWallet.fund(funds, "INR");
        return new PortfolioService(provider, clock, currency);
    }

    private static PortfolioItem item(String sym, int qty, double price, String cur) {
        PortfolioItem i = new PortfolioItem(sym, sym, qty, price, price);
        i.setOriginalCurrency(cur);
        return i;
    }

    private static long count(String table) throws SQLException {
        try (Statement st = DatabaseManager.getConnection().createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.getLong(1);
        }
    }

    private static double balance() throws SQLException {
        try (Statement st = DatabaseManager.getConnection().createStatement(); ResultSet rs = st.executeQuery("SELECT balance FROM wallet WHERE id = 1")) {
            return rs.getDouble(1);
        }
    }

    private static TradeBlockedException blocked(org.junit.jupiter.api.function.Executable e, Reason expected) {
        TradeBlockedException t = assertThrows(TradeBlockedException.class, e);
        assertEquals(expected, t.getReason(), t.getMessage());
        return t;
    }

    // ---------------------------------------------------------------- freshness policy

    @Test
    void displayStatusIsFreshStaleOrUnavailable() {
        Instant now = clock.instant();
        assertEquals(QuotePolicy.Status.UNAVAILABLE, QuotePolicy.displayStatus(null, now));
        assertEquals(QuotePolicy.Status.FRESH, QuotePolicy.displayStatus(now.minus(Duration.ofMinutes(15)), now));
        assertEquals(QuotePolicy.Status.STALE, QuotePolicy.displayStatus(now.minus(Duration.ofMinutes(15)).minusSeconds(1), now));
        assertTrue(QuotePolicy.describe(null, now).startsWith("Unavailable"));
        assertEquals("Fresh (3 min ago)", QuotePolicy.describe(now.minus(Duration.ofMinutes(3)), now));
        assertTrue(QuotePolicy.describe(now.minus(Duration.ofHours(5)), now).startsWith("Stale (5 h ago"));
    }

    @Test
    void holdingsReportTheirQuoteStatusThroughTheService() {
        PortfolioService s = inrWallet(10_000);
        s.buyStock(item("AAA", 1, 10.0, "INR"));
        PortfolioItem held = s.getAllPortfolioItems().get(0);
        assertEquals(QuotePolicy.Status.UNAVAILABLE, s.quoteStatus(held)); // never refreshed: only the price paid is known
        provider.prices.put("AAA", 11.0);
        provider.currencies.put("AAA", "INR");
        assertEquals(0, s.updateAllPrices());
        assertEquals(QuotePolicy.Status.FRESH, s.quoteStatus(s.getAllPortfolioItems().get(0)));
        clock.advance(Duration.ofMinutes(16));
        assertEquals(QuotePolicy.Status.STALE, s.quoteStatus(s.getAllPortfolioItems().get(0)));
    }

    @Test
    void theServiceRejectsAStaleQuoteEvenIfTheUiKeptItOpen() throws Exception {
        PortfolioService s = inrWallet(10_000);
        s.buyStock(item("AAA", 10, 10.0, "INR"));
        provider.prices.put("AAA", 12.0);
        provider.currencies.put("AAA", "INR");
        Quote q = s.fetchValidatedQuote("AAA");

        clock.advance(Duration.ofSeconds(60)); // exactly at the limit: still tradable
        assertDoesNotThrow(() -> s.sellStock("AAA", 1, q));
        clock.advance(Duration.ofSeconds(1));  // 61 s old
        double before = balance();
        blocked(() -> s.sellStock("AAA", 1, q), Reason.STALE_QUOTE);
        blocked(() -> s.buyStock("AAA", 1, q), Reason.STALE_QUOTE);
        assertEquals(before, balance(), 0.0);
        assertEquals(9, s.getAllPortfolioItems().get(0).getQuantity());
        assertEquals(2, count("transactions")); // the original buy and the one allowed sale

        Quote future = new Quote("AAA", 12.0, "INR", clock.instant().plusSeconds(5));
        blocked(() -> s.sellStock("AAA", 1, future), Reason.STALE_QUOTE);
        Quote fresh = s.fetchValidatedQuote("AAA");
        assertDoesNotThrow(() -> s.sellStock("AAA", 1, fresh));
    }

    // ---------------------------------------------------------------- missing / unusable quotes

    @Test
    void aMissingOrBrokenQuoteBlocksTheTradeAndChangesNothing() throws Exception {
        PortfolioService s = inrWallet(10_000);
        s.buyStock(item("AAA", 10, 10.0, "INR"));
        double before = balance();

        assertThrows(PriceUnavailableException.class, () -> s.fetchValidatedQuote("AAA"), "provider has no quote");
        provider.failure = new PriceUnavailableException(PriceUnavailableException.Reason.RATE_LIMIT, "quota");
        assertThrows(PriceUnavailableException.class, () -> s.fetchValidatedQuote("AAA"));
        provider.failure = null;
        for (double bad : new double[] { 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY }) {
            provider.prices.put("AAA", bad);
            provider.currencies.put("AAA", "INR");
            assertThrows(PriceUnavailableException.class, () -> s.fetchValidatedQuote("AAA"), "price " + bad);
        }
        provider.prices.put("AAA", 12.0);
        provider.currencies.remove("AAA"); // the provider does not state a currency
        assertFalse(s.sellStock("AAA", 1), "currency not stated -> compat sell reports failure");
        assertNotNull(s.getLastTradeProblem());
        assertEquals(before, balance(), 0.0);
        assertEquals(10, s.getAllPortfolioItems().get(0).getQuantity());
    }

    @Test
    void aQuoteWithoutAStatedCurrencyCannotBeTradedOn() throws Exception {
        PortfolioService s = inrWallet(10_000);
        s.buyStock(item("AAA", 10, 10.0, "INR"));
        provider.prices.put("AAA", 12.0); // currency deliberately not set
        blocked(() -> s.fetchValidatedQuote("AAA"), Reason.QUOTE_CURRENCY_UNKNOWN);
        Quote handBuilt = new Quote("AAA", 12.0, null, clock.instant());
        blocked(() -> s.sellStock("AAA", 1, handBuilt), Reason.QUOTE_CURRENCY_UNKNOWN);
        blocked(() -> s.buyStock("AAA", 1, handBuilt), Reason.QUOTE_CURRENCY_UNKNOWN);
        assertEquals(10, s.getAllPortfolioItems().get(0).getQuantity());
    }

    @Test
    void aQuoteForAnotherSymbolIsRefused() {
        PortfolioService s = inrWallet(10_000);
        s.buyStock(item("AAA", 10, 10.0, "INR"));
        Quote other = new Quote("BBB", 12.0, "INR", clock.instant());
        assertThrows(IllegalArgumentException.class, () -> s.sellStock("AAA", 1, other));
    }

    // ---------------------------------------------------------------- currency mismatch

    @Test
    void aHoldingIsNeverSoldOrBoughtAtAPriceInAnotherCurrency() throws Exception {
        PortfolioService s = inrWallet(100_000);
        s.buyStock(item("AAA", 10, 100.0, "INR")); // held in rupees
        provider.prices.put("AAA", 12.0);
        provider.currencies.put("AAA", "USD");
        double before = balance();

        Quote usd = s.fetchValidatedQuote("AAA");
        assertEquals("USD", usd.getCurrency());
        blocked(() -> s.sellStock("AAA", 1, usd), Reason.CURRENCY_MISMATCH);
        blocked(() -> s.buyStock("AAA", 1, usd), Reason.CURRENCY_MISMATCH);
        blocked(() -> s.describeWalletEffect("AAA", 1, usd, false), Reason.CURRENCY_MISMATCH);
        blocked(() -> s.buyStock(item("AAA", 1, 12.0, "USD")), Reason.CURRENCY_MISMATCH); // averaging INR with USD cost
        assertEquals(before, balance(), 0.0);
        assertEquals(10, s.getAllPortfolioItems().get(0).getQuantity());
        assertEquals(1, count("transactions"));
    }

    // ---------------------------------------------------------------- conversion

    @Test
    void aForeignCurrencyTradeIsConvertedAtAValidatedRateAndRecorded() throws Exception {
        PortfolioService s = inrWallet(100_000);
        rates.rates.put("USD>INR", 83.5);
        s.buyStock(item("AAA", 10, 10.0, "USD")); // 100 USD = 8350.00 INR
        assertEquals(100_000 - 8350.00, balance(), 0.005);
        assertEquals("USD", s.getAllPortfolioItems().get(0).getOriginalCurrency());
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT amount, description FROM wallet_transactions ORDER BY id DESC LIMIT 1")) {
            assertEquals(8350.00, rs.getDouble("amount"), 0.005, "the wallet amount is in rupees, not the dollar figure");
            String d = rs.getString("description");
            assertTrue(d.contains("100.00 USD") && d.contains("8350.00 INR") && d.contains("83.5") && d.contains("test-source"), d);
        }
        BigDecimal realized = s.sellStock("AAA", 5, 12.0); // 60 USD = 5010.00 INR; P/L in USD
        assertEquals(new BigDecimal("10.00"), realized);
        assertEquals(100_000 - 8350.00 + 5010.00, balance(), 0.005);
    }

    @Test
    void aTradeNeedingConversionIsBlockedWhenNoValidRateExists() throws Exception {
        PortfolioService s = inrWallet(100_000);
        // 1. the source is down
        rates.failure = new IOException("connection refused");
        blocked(() -> s.buyStock(item("AAA", 1, 10.0, "USD")), Reason.RATE_UNAVAILABLE);
        assertEquals(100_000, balance(), 0.0);
        assertEquals(0, count("portfolio_items"));
        assertEquals(0, count("transactions"));
        assertEquals(1, count("wallet_transactions"), "only the funding row");
        rates.failure = null;

        // 2. invalid answers are never used (each scenario waits out the short failure memory so the source is really asked)
        for (double bad : new double[] { 0.0, -83.0, Double.NaN, Double.POSITIVE_INFINITY }) {
            clock.advance(CurrencyService.FAILURE_MEMORY.plusSeconds(1));
            rates.rates.put("USD>INR", bad);
            int asked = rates.fetches;
            blocked(() -> s.buyStock(item("AAA", 1, 10.0, "USD")), Reason.RATE_UNAVAILABLE);
            assertEquals(asked + 1, rates.fetches, "the source was consulted and its invalid answer refused: " + bad);
        }
        // 3. an answer for a different pair is not accepted
        clock.advance(CurrencyService.FAILURE_MEMORY.plusSeconds(1));
        rates.rates.put("USD>INR", 83.0);
        rates.pairOverride = "EUR";
        blocked(() -> s.buyStock(item("AAA", 1, 10.0, "USD")), Reason.RATE_UNAVAILABLE);
        rates.pairOverride = null;
        // 4. an outdated answer is not accepted
        clock.advance(CurrencyService.FAILURE_MEMORY.plusSeconds(1));
        rates.stampOverride = clock.instant().minus(Duration.ofHours(2));
        blocked(() -> s.buyStock(item("AAA", 1, 10.0, "USD")), Reason.RATE_UNAVAILABLE);
        rates.stampOverride = null;
        clock.advance(CurrencyService.FAILURE_MEMORY.plusSeconds(1));
        assertEquals(100_000, balance(), 0.0);
        assertEquals(0, count("transactions"));

        // and the same trade works once a valid rate exists
        assertDoesNotThrow(() -> s.buyStock(item("AAA", 1, 10.0, "USD")));
        assertEquals(100_000 - 830.0, balance(), 0.005);
    }

    @Test
    void aSaleNeedingConversionIsBlockedWithoutARateAndLeavesTheHoldingAlone() throws Exception {
        PortfolioService s = inrWallet(100_000);
        rates.rates.put("USD>INR", 80.0);
        s.buyStock(item("AAA", 10, 10.0, "USD"));
        double before = balance();
        rates.rates.clear();
        clock.advance(Duration.ofHours(2)); // the cached rate has expired and the source has none
        blocked(() -> s.sellStock("AAA", 5, 12.0), Reason.RATE_UNAVAILABLE);
        assertEquals(before, balance(), 0.0);
        assertEquals(10, s.getAllPortfolioItems().get(0).getQuantity());
        assertEquals(1, count("transactions"));
        assertEquals(new BigDecimal("0.00"), s.getTotalRealizedProfitLoss());
    }

    @Test
    void ratesAreCachedForAnHourThenRefetched() throws Exception {
        rates.rates.put("USD>INR", 80.0);
        CurrencyService.Rate first = currency.requireRate("usd", "inr");
        assertEquals("test-source", first.source());
        assertEquals(1, rates.fetches);
        clock.advance(Duration.ofMinutes(59));
        currency.requireRate("USD", "INR");
        assertEquals(1, rates.fetches, "still fresh: no second fetch");
        rates.rates.put("USD>INR", 81.0);
        clock.advance(Duration.ofMinutes(2));
        assertEquals(81.0, currency.requireRate("USD", "INR").rate(), 0.0);
        assertEquals(2, rates.fetches);
        assertEquals(1.0, currency.requireRate("INR", "inr").rate(), 0.0, "same currency needs no source");
        assertEquals(2, rates.fetches);
    }

    @Test
    void displayConversionMayApproximateButSaysSoAndNeverMovesMoney() {
        rates.failure = new IOException("down");
        assertFalse(currency.usedFallbackRates());
        double shown = currency.convert(83.0, "INR", "USD");
        assertEquals(1.0, shown, 1e-9);
        assertTrue(currency.usedFallbackRates(), "the caller can tell the figure is approximate");
        assertThrows(TradeBlockedException.class, () -> currency.requireRate("INR", "USD"), "but money never uses it");
    }

    // ---------------------------------------------------------------- rollback

    @Test
    void aDatabaseFailureDuringAConvertedTradeRollsBackEverything() throws Exception {
        PortfolioService s = inrWallet(100_000);
        rates.rates.put("USD>INR", 80.0);
        s.buyStock(item("AAA", 10, 10.0, "USD")); // 8000 INR
        double afterBuy = balance();
        DatabaseManager.getConnection().createStatement().execute(
                "CREATE TRIGGER boom BEFORE INSERT ON transactions BEGIN SELECT RAISE(ABORT, 'injected failure'); END");

        assertThrows(IllegalStateException.class, () -> s.sellStock("AAA", 4, 20.0));
        assertThrows(IllegalStateException.class, () -> s.buyStock(item("BBB", 1, 5.0, "USD")));
        assertEquals(afterBuy, balance(), 0.0);
        assertEquals(afterBuy, s.getWalletService().getBalance(), 0.0);
        assertEquals(10, s.getAllPortfolioItems().get(0).getQuantity());
        assertEquals(1, s.getAllPortfolioItems().size());
        assertEquals(1, count("transactions"));
        assertEquals(2, count("wallet_transactions")); // funding + the first buy
    }

    // ---------------------------------------------------------------- refresh across currencies

    @Test
    void refreshConvertsAForeignQuoteAtAValidatedRateOrLeavesThePriceAlone() {
        PortfolioService s = inrWallet(100_000);
        s.buyStock(item("AAA", 10, 800.0, "INR"));
        provider.prices.put("AAA", 10.0);

        // quote currency unknown: not updated
        assertEquals(1, s.updateAllPrices());
        assertEquals(800.0, s.getAllPortfolioItems().get(0).getCurrentPrice(), 0.0);
        assertNull(s.getAllPortfolioItems().get(0).getPriceUpdatedAt());
        assertTrue(s.getLastRefreshProblem().contains("currency"));

        // USD quote, held in INR, no rate: not updated, reason recorded
        provider.currencies.put("AAA", "USD");
        rates.failure = new IOException("down");
        assertEquals(1, s.updateAllPrices());
        assertEquals(800.0, s.getAllPortfolioItems().get(0).getCurrentPrice(), 0.0);
        assertTrue(s.getLastRefreshProblem().contains("USD") && s.getLastRefreshProblem().contains("INR"));

        // with a valid rate: stored in the holding's currency
        rates.failure = null;
        rates.rates.put("USD>INR", 85.0);
        assertEquals(0, s.updateAllPrices());
        assertEquals(850.0, s.getAllPortfolioItems().get(0).getCurrentPrice(), 0.0001);
        assertEquals(QuotePolicy.Status.FRESH, s.quoteStatus(s.getAllPortfolioItems().get(0)));
    }

    // ---------------------------------------------------------------- wallet currency + quote-based trades

    @Test
    void theWalletHasOneCurrencyThatCanOnlyChangeWhileItIsEmpty() {
        assertNotNull(DatabaseManager.getConnection());
        WalletService w = new WalletService();
        assertEquals("INR", w.getCurrency(), "default for a new wallet");
        assertFalse(w.setCurrency("rupees"));
        assertTrue(w.setCurrency("usd"));
        assertEquals("USD", w.getCurrency());
        assertTrue(w.addFunds(100, "test"));
        assertFalse(w.setCurrency("EUR"), "relabelling real money would change its value");
        assertEquals("USD", w.getCurrency());
    }

    @Test
    void aQuoteTradeInTheWalletCurrencyNeedsNoRateAndStampsTheQuoteTime() throws Exception {
        PortfolioService s = inrWallet(10_000);
        provider.prices.put("AAA", 100.0);
        provider.currencies.put("AAA", "INR");
        Quote q = s.fetchValidatedQuote("AAA");
        assertTrue(s.describeWalletEffect("AAA", 5, q, true).contains("500.00 INR"));
        s.buyStock("AAA", 5, q);
        assertEquals(0, rates.fetches);
        assertEquals(9_500.0, balance(), 0.0);
        PortfolioItem h = s.getAllPortfolioItems().get(0);
        assertEquals("INR", h.getOriginalCurrency());
        assertEquals(q.getFetchedAt(), h.getPriceUpdatedAt());
        assertEquals(QuotePolicy.Status.FRESH, s.quoteStatus(h));
    }

    @Test
    void anImpossibleSaleIsRefusedBeforeConfirmation() {
        PortfolioService s = inrWallet(10_000);
        s.buyStock(item("AAA", 2, 10.0, "INR"));
        Quote q = new Quote("AAA", 10.0, "INR", clock.instant());
        assertThrows(IllegalArgumentException.class, () -> s.describeWalletEffect("AAA", 3, q, false));
        assertThrows(IllegalArgumentException.class, () -> s.describeWalletEffect("ZZZ", 1, new Quote("ZZZ", 1.0, "INR", clock.instant()), false));
    }
}
