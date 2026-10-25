package com.portfolio.service;

import com.portfolio.database.DatabaseManager;
import com.portfolio.model.PortfolioItem;
import com.portfolio.model.PriceAlert;
import com.portfolio.model.Stock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Watchlist persistence/refresh and price alerts, with a fake provider and a temporary database. */
class AlertAndWatchlistTest {
    @TempDir Path tmp;
    final Map<String, Double> prices = new HashMap<>();

    private final StockPriceService provider = new StockPriceService() {
        @Override
        public Stock getStock(String symbol) {
            Double p = prices.get(symbol);
            if (p == null) {
                return null;
            }
            Stock s = new Stock(symbol, symbol, p);
            s.setCurrency("USD");
            s.setChangePercent(2.5);
            return s;
        }

        @Override
        public boolean isValidStock(String symbol) {
            return getStock(symbol) != null;
        }
    };

    @BeforeEach
    void setUp() {
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("a.db").toString());
        com.portfolio.TestWallet.fund();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    @Test
    void watchlistPersistsRejectsDuplicatesAndRemoves() {
        PortfolioService s = new PortfolioService(provider);
        assertTrue(s.addToWatchlist("MSFT", "Microsoft"));
        assertFalse(s.addToWatchlist("msft", "Microsoft"), "duplicates (any case) are refused");
        DatabaseManager.closeConnection();
        PortfolioService again = new PortfolioService(provider);
        assertEquals(1, again.getWatchlist().size());
        again.removeFromWatchlist("MSFT");
        DatabaseManager.closeConnection();
        assertTrue(new PortfolioService(provider).getWatchlist().isEmpty());
    }

    @Test
    void refreshUpdatesWatchlistWithRealQuotesAndKeepsUnquotedOnesUntouched() {
        PortfolioService s = new PortfolioService(provider);
        s.addToWatchlist("MSFT", "Microsoft");
        s.addToWatchlist("NOQUOTE", "No Quote Inc");
        prices.put("MSFT", 410.0);
        assertEquals(0, s.updateAllPrices(), "no holdings, so no holding failures");
        Stock msft = s.getWatchlist().stream().filter(w -> w.getSymbol().equals("MSFT")).findFirst().get();
        Stock none = s.getWatchlist().stream().filter(w -> w.getSymbol().equals("NOQUOTE")).findFirst().get();
        assertEquals(410.0, msft.getCurrentPrice(), 0.0);
        assertEquals(2.5, msft.getChangePercent(), 0.0);
        assertEquals(0.0, none.getCurrentPrice(), 0.0, "an unknown symbol gets no invented price");
        assertNotNull(s.getLastRefreshProblem());
    }

    @Test
    void refreshCarriesTheProvidersDailyChangeToHoldings() {
        PortfolioService s = new PortfolioService(provider);
        s.buyStock(new PortfolioItem("AAA", "AAA", 1, 10.0, 10.0));
        prices.put("AAA", 12.0);
        assertEquals(0, s.updateAllPrices());
        PortfolioItem a = s.getAllPortfolioItems().get(0);
        assertEquals(12.0, a.getCurrentPrice(), 0.0);
        assertEquals(2.5, a.getStock().getChangePercent(), 0.0);
        assertNotNull(a.getPriceUpdatedAt());
        assertEquals(1, s.getTopGainers(5).size());
    }

    @Test
    void alertTriggersOnceWhenARefreshCrossesItsTarget() {
        PortfolioService s = new PortfolioService(provider);
        s.buyStock(new PortfolioItem("AAA", "AAA", 1, 10.0, 10.0));
        PriceAlert above = new PriceAlert("AAA", 15.0, true);
        PriceAlert below = new PriceAlert("AAA", 5.0, false);
        s.addPriceAlert(above);
        s.addPriceAlert(below);

        prices.put("AAA", 12.0);
        s.updateAllPrices();
        assertFalse(above.isTriggered());
        assertFalse(below.isTriggered());

        prices.put("AAA", 16.0);
        s.updateAllPrices();
        assertTrue(above.isTriggered(), "price reached the target");
        assertFalse(below.isTriggered());
    }

    @Test
    void manualPriceUpdateValidatesAndTriggersAlerts() {
        PortfolioService s = new PortfolioService(provider);
        s.buyStock(new PortfolioItem("AAA", "AAA", 1, 10.0, 10.0));
        PriceAlert alert = new PriceAlert("AAA", 8.0, false);
        s.addPriceAlert(alert);
        assertThrows(IllegalArgumentException.class, () -> s.updateStockPrice("AAA", 0));
        assertThrows(IllegalArgumentException.class, () -> s.updateStockPrice("AAA", Double.NaN));
        assertFalse(alert.isTriggered());
        s.updateStockPrice("AAA", 7.5);
        assertTrue(alert.isTriggered());
        assertEquals(7.5, s.getAllPortfolioItems().get(0).getCurrentPrice(), 0.0);
    }
}
