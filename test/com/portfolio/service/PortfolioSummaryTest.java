package com.portfolio.service;

import com.portfolio.database.DatabaseManager;
import com.portfolio.model.PortfolioItem;
import com.portfolio.model.PriceAlert;
import com.portfolio.model.Stock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** What the home screen shows: every figure is real, labelled, and absent when unknown. */
class PortfolioSummaryTest {
    @TempDir Path tmp;
    final TradePolicyTest.MutableClock clock = new TradePolicyTest.MutableClock();
    final TradePolicyTest.FakeRates rates = new TradePolicyTest.FakeRates(clock);

    /** Quotes with a controllable daily change. */
    final Map<String, double[]> quotes = new HashMap<>(); // symbol -> {price, change%}
    final StockPriceService provider = new StockPriceService() {
        @Override
        public Stock getStock(String symbol) {
            double[] q = quotes.get(symbol);
            if (q == null) return null;
            Stock s = new Stock(symbol, symbol, q[0]);
            s.setChangePercent(q[1]);
            s.setCurrency("USD");
            return s;
        }

        @Override public boolean isValidStock(String symbol) { return getStock(symbol) != null; }
    };

    @BeforeEach
    void setUp() {
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("s.db").toString());
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    private PortfolioService usdService(double funds) {
        com.portfolio.TestWallet.fund(funds, "USD");
        PortfolioService s = new PortfolioService(provider, clock, new CurrencyService(rates, clock));
        s.setBaseCurrency("USD");
        return s;
    }

    private static PortfolioItem item(String sym, int qty, double price) {
        PortfolioItem i = new PortfolioItem(sym, sym, qty, price, price);
        i.setOriginalCurrency("USD");
        return i;
    }

    @Test
    void aBrandNewPortfolioIsEmptyAndShowsNoNumbersItDoesNotHave() {
        DatabaseManager.getConnection();
        PortfolioService s = new PortfolioService(provider, clock, new CurrencyService(rates, clock));
        PortfolioSummary sum = PortfolioSummary.of(s);
        assertTrue(sum.isEmpty());
        assertEquals(0, sum.holdings());
        assertEquals(0, sum.fresh() + sum.stale() + sum.unavailable());
        assertTrue(sum.recent().isEmpty() && sum.watchlist().isEmpty() && sum.topGainers().isEmpty() && sum.topLosers().isEmpty());
        assertEquals(0.0, sum.walletBalance(), 0.0);
        assertEquals("INR", sum.walletCurrency());
        assertFalse(sum.approximate());
    }

    @Test
    void holdingsAreCountedByQuoteStatusAndUnrefreshedOnesAreValuedAtCost() {
        PortfolioService s = usdService(10_000);
        s.buyStock(item("AAA", 10, 10.0));
        s.buyStock(item("BBB", 5, 20.0));
        PortfolioSummary before = PortfolioSummary.of(s);
        assertEquals(2, before.holdings());
        assertEquals(2, before.unavailable());
        assertEquals(2, before.valuedAtCost());
        assertEquals(200.0, before.totalValue().value(), 0.001, "valued at what was paid: 10x10 + 5x20");
        assertEquals(0.0, before.profitLoss().value(), 0.001);

        quotes.put("AAA", new double[] { 12.0, 2.5 });
        quotes.put("BBB", new double[] { 18.0, -1.5 });
        s.updateAllPrices();
        PortfolioSummary fresh = PortfolioSummary.of(s);
        assertEquals(2, fresh.fresh());
        assertEquals(0, fresh.unavailable());
        assertEquals(10 * 12.0 + 5 * 18.0, fresh.totalValue().value(), 0.001);
        assertEquals(10 * 12.0 + 5 * 18.0 - 200.0, fresh.profitLoss().value(), 0.001);

        clock.advance(Duration.ofMinutes(20));
        PortfolioSummary stale = PortfolioSummary.of(s);
        assertEquals(2, stale.stale());
        assertEquals(0, stale.fresh());
    }

    @Test
    void moversComeOnlyFromRealRefreshedQuotesSortedByTheirReportedChange() {
        PortfolioService s = usdService(100_000);
        for (String sym : new String[] { "UP1", "UP2", "DN1", "FLAT", "NOREFRESH" }) {
            s.buyStock(item(sym, 1, 10.0));
            if (!sym.equals("NOREFRESH")) {
                quotes.put(sym, new double[] { 11.0, switch (sym) { case "UP1" -> 5.0; case "UP2" -> 2.0; case "DN1" -> -3.0; default -> 0.0; } });
            }
        }
        quotes.put("NOREFRESH", new double[] { 99.0, 50.0 }); // exists at the provider but is never refreshed into the holding
        // refresh everything except NOREFRESH by removing its quote for the refresh, then restoring
        double[] hidden = quotes.remove("NOREFRESH");
        s.updateAllPrices();
        quotes.put("NOREFRESH", hidden);
        PortfolioSummary sum = PortfolioSummary.of(s);
        assertEquals(java.util.List.of("UP1", "UP2"), sum.topGainers().stream().map(PortfolioItem::getSymbol).toList());
        assertEquals(java.util.List.of("DN1"), sum.topLosers().stream().map(PortfolioItem::getSymbol).toList());
        assertFalse(sum.topGainers().stream().anyMatch(i -> i.getSymbol().equals("NOREFRESH")), "an unrefreshed holding is never a 'mover'");
        assertFalse(sum.topGainers().stream().anyMatch(i -> i.getSymbol().equals("FLAT")), "zero change is not a move");
    }

    @Test
    void recentActivityIsNewestFirstAndCappedAtFive() {
        PortfolioService s = usdService(100_000);
        for (int i = 1; i <= 7; i++) {
            s.buyStock(item("S" + i, 1, 10.0));
            clock.advance(Duration.ofSeconds(1));
        }
        PortfolioSummary sum = PortfolioSummary.of(s);
        assertEquals(5, sum.recent().size());
        assertEquals("S7", sum.recent().get(0).getSymbol());
        assertEquals("S3", sum.recent().get(4).getSymbol());
    }

    @Test
    void realizedProfitWalletAndAlertsAreReported() {
        PortfolioService s = usdService(10_000);
        s.buyStock(item("AAA", 10, 10.0)); // -100
        s.sellStock("AAA", 4, 15.0);       // +60, realized 20
        s.addPriceAlert(new PriceAlert("AAA", 100.0, true));
        PriceAlert hit = new PriceAlert("AAA", 1.0, false);
        s.addPriceAlert(hit);
        hit.setTriggered(true);
        s.addToWatchlist("MSFT", "Microsoft");
        PortfolioSummary sum = PortfolioSummary.of(s);
        assertEquals(new BigDecimal("20.00"), sum.realizedPl());
        assertEquals(10_000 - 100 + 60, sum.walletBalance(), 0.001);
        assertEquals("USD", sum.walletCurrency());
        assertEquals(1, sum.activeAlerts());
        assertEquals(1, sum.triggeredAlerts());
        assertEquals(1, sum.watchlist().size());
        assertFalse(sum.isEmpty());
    }

    @Test
    void approximateRatesAreFlaggedAndValidatedOnesAreNot() {
        com.portfolio.TestWallet.fund(10_000, "USD");
        PortfolioService s = new PortfolioService(provider, clock, new CurrencyService(rates, clock));
        s.buyStock(item("AAA", 10, 10.0));
        s.setBaseCurrency("INR");
        rates.rates.put("USD>INR", 80.0);
        PortfolioSummary ok = PortfolioSummary.of(s);
        assertFalse(ok.approximate());
        assertEquals(100.0 * 80.0, ok.totalValue().value(), 0.001);

        clock.advance(CurrencyService.RATE_MAX_AGE.plusMinutes(1));
        rates.failure = new IOException("down");
        PortfolioSummary approx = PortfolioSummary.of(s);
        assertTrue(approx.approximate());
        assertTrue(approx.totalValue().approximate() && approx.invested().approximate() && approx.profitLoss().approximate());
        assertEquals("≈ ", approx.totalValue().mark());
    }

    @Test
    void theLastRefreshProblemIsExposedSoTheScreenCanExplainIt() {
        PortfolioService s = usdService(10_000);
        s.buyStock(item("AAA", 1, 10.0));
        s.updateAllPrices(); // provider has no quote for AAA
        assertNotNull(PortfolioSummary.of(s).refreshProblem());
    }
}
