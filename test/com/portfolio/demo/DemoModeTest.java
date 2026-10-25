package com.portfolio.demo;

import com.portfolio.database.DatabaseManager;
import com.portfolio.model.PortfolioItem;
import com.portfolio.model.Stock;
import com.portfolio.service.NewsService;
import com.portfolio.service.PortfolioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The demo data is deterministic, internally consistent, and only ever written to a database the test (or demo mode) chose. */
class DemoModeTest {
    @TempDir Path tmp;

    @BeforeEach
    void db() {
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("demo-test.db").toString());
    }

    @AfterEach
    void close() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    @Test
    void sampleFiguresAddUp() {
        PortfolioService p = DemoMode.seed();

        // wallet: 500,000 + 250,000 deposited, 50,000 withdrawn, 370,000 spent on six buys, 15,750 from selling 10 INFY
        double spent = 0;
        for (DemoMode.Holding h : DemoMode.HOLDINGS) {
            spent += h.quantity() * h.buy();
        }
        double expectedBalance = DemoMode.DEPOSIT_1 + DemoMode.DEPOSIT_2 - DemoMode.WITHDRAWAL - spent
                + DemoMode.INFY_SOLD * DemoMode.INFY_SELL_PRICE;
        assertEquals(345_750.00, expectedBalance, 0.001);
        assertEquals(expectedBalance, p.getWalletService().getBalance(), 0.005);
        assertEquals(750_000.00, p.getWalletService().getTotalDeposited(), 0.005);
        assertEquals(50_000.00, p.getWalletService().getTotalWithdrawn(), 0.005);

        // holdings: six, with INFY reduced by the sale; invested and current value follow from the fixed prices
        assertEquals(6, p.getAllPortfolioItems().size());
        double invested = 0;
        double value = 0;
        for (PortfolioItem item : p.getAllPortfolioItems()) {
            double price = DemoPriceService.priceOf(item.getSymbol());
            assertEquals(price, item.getCurrentPrice(), 0.0001, item.getSymbol());
            invested += item.getQuantity() * item.getPurchasePrice();
            value += item.getQuantity() * price;
        }
        assertEquals(30, p.getAllPortfolioItems().stream().filter(i -> i.getSymbol().equals("INFY")).findFirst().orElseThrow().getQuantity());
        assertEquals(354_600.00, invested, 0.005);
        assertEquals(367_241.30, value, 0.005);
        assertEquals(invested, p.getTotalInvestment(), 0.005);
        assertEquals(value, p.getTotalPortfolioValue(), 0.005);
        assertEquals(value - invested, p.getTotalProfitLoss(), 0.005);
        assertEquals((value - invested) / invested * 100, p.getReturnPercentage(), 0.005);

        assertEquals(7, p.getAllTransactions().size(), "six buys and one sell");
        assertEquals(DemoMode.WATCHLIST.size(), p.getWatchlist().size());
    }

    @Test
    void theSameSeedGivesTheSameNumbersEveryTime() {
        double first = DemoMode.seed().getTotalPortfolioValue();
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("second.db").toString());
        assertEquals(first, DemoMode.seed().getTotalPortfolioValue(), 0.0);
    }

    @Test
    void demoDataIsRefusedUnlessDemoModeWasActivated() {
        assertFalse(DemoMode.isActive());
        assertThrows(IllegalStateException.class, DemoMode::seededPortfolio);
        assertFalse(DemoMode.requested(new String[] { "--dashboard" }));
        assertTrue(DemoMode.requested(new String[] { "--demo" }));
    }

    @Test
    void demoQuotesAreInRupeesAndUnknownSymbolsAreNotInvented() {
        DemoPriceService prices = new DemoPriceService();
        Stock s = prices.getStock("reliance");
        assertEquals("INR", s.getCurrency());
        assertEquals(2914.50, s.getCurrentPrice(), 0.0001);
        assertNull(prices.getStock("NOSUCHSYMBOL"));
        assertFalse(prices.isValidStock(null));
    }

    @Test
    void demoHeadlinesAreLabelledAsSimulated() {
        for (NewsService.NewsItem n : DemoMode.news()) {
            assertEquals("StockVault demo data", n.getSource());
            assertTrue(n.getDescription().contains("Not real news"));
        }
    }

    @Test
    void demoHistoryIsDeterministicEndsAtTheQuoteAndPassesThroughThePricePaid() {
        java.time.LocalDate today = java.time.LocalDate.of(2026, 10, 26);
        java.util.List<double[]> a = DemoPriceService.history("RELIANCE", today);
        java.util.List<double[]> b = DemoPriceService.history("RELIANCE", today);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertArrayEquals(a.get(i), b.get(i), 0.0);
        }
        assertEquals(2914.50, a.get(a.size() - 1)[1], 0.005, "the last close is the demo quote");
        java.time.LocalDate buyDay = today.minusDays(DemoPriceService.anchorDays("RELIANCE"));
        double onBuyDay = a.stream().filter(p -> java.time.LocalDate.ofEpochDay((long) p[0]).equals(buyDay)
                || java.time.LocalDate.ofEpochDay((long) p[0]).isAfter(buyDay)).findFirst().orElseThrow()[1];
        assertEquals(2780.00, onBuyDay, 0.005, "the series passes through the price paid on the purchase day");
        assertTrue(a.stream().noneMatch(p -> {
            java.time.DayOfWeek d = java.time.LocalDate.ofEpochDay((long) p[0]).getDayOfWeek();
            return d == java.time.DayOfWeek.SATURDAY || d == java.time.DayOfWeek.SUNDAY;
        }), "weekdays only");
        assertThrows(com.portfolio.service.PriceUnavailableException.class, () -> DemoPriceService.history("NOSUCH", today));
    }

    @Test
    void walletLedgerBalancesChainFromOneRowToTheNext() {
        PortfolioService p = DemoMode.seed();
        java.util.List<com.portfolio.model.WalletTransaction> rows = new java.util.ArrayList<>(p.getWalletService().getTransactions());
        java.util.Collections.reverse(rows); // oldest first
        double running = 0;
        for (com.portfolio.model.WalletTransaction t : rows) {
            boolean credit = t.getType().contains("DEPOSIT") || t.getType().contains("SELL") || t.getType().contains("SALE");
            running += credit ? t.getAmount() : -t.getAmount();
            assertEquals(running, t.getBalanceAfter(), 0.005, t.getType());
        }
        assertEquals(p.getWalletService().getBalance(), running, 0.005);
    }
}
