package com.portfolio.service;

import com.portfolio.model.Stock;
import com.portfolio.service.PriceUnavailableException.Reason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Failover, history parsing, Alpha Vantage history/limit handling and key resolution. No network, no real keys. */
class ProviderLayerTest {
    @TempDir Path tmp;

    @AfterEach
    void clean() {
        System.clearProperty("stockvault.config");
        ApiKeyManager.resetForTests();
    }

    private static StockPriceService returning(Stock s) {
        return new StockPriceService() {
            @Override public Stock getStock(String symbol) { return s; }
            @Override public boolean isValidStock(String symbol) { return s != null; }
        };
    }

    private static StockPriceService failing(Reason r) {
        return new StockPriceService() {
            @Override public Stock getStock(String symbol) { throw new PriceUnavailableException(r, "fake " + r); }
            @Override public boolean isValidStock(String symbol) { return getStock(symbol) != null; }
        };
    }

    // ---- failover chain

    @Test
    void failoverSkipsFailedProvidersAndReturnsTheFirstRealQuote() {
        Stock real = new Stock("A", "A", 42.5);
        MultiSourceStockService m = new MultiSourceStockService(List.of(failing(Reason.NO_KEY), failing(Reason.RATE_LIMIT), returning(real)));
        assertSame(real, m.getStock("A"));
        assertEquals(42.5, m.getCurrentPrice("A"), 0.0);
    }

    @Test
    void whenEveryProviderFailsThereIsNoPriceOnlyTheMostInformativeError() {
        MultiSourceStockService m = new MultiSourceStockService(List.of(failing(Reason.NO_KEY), failing(Reason.RATE_LIMIT), failing(Reason.NO_KEY)));
        PriceUnavailableException e = assertThrows(PriceUnavailableException.class, () -> m.getStock("A"));
        assertEquals(Reason.RATE_LIMIT, e.getReason(), "a real failure outranks 'no key'");
        assertThrows(PriceUnavailableException.class, () -> m.getCurrentPrice("A"));
    }

    @Test
    void whenNoProviderHasAKeyTheMessageNamesEverySetting() {
        MultiSourceStockService m = new MultiSourceStockService(List.of(failing(Reason.NO_KEY), failing(Reason.NO_KEY)));
        PriceUnavailableException e = assertThrows(PriceUnavailableException.class, () -> m.getStock("A"));
        assertEquals(Reason.NO_KEY, e.getReason());
        assertTrue(e.getMessage().startsWith("No API key configured for any price provider"), e.getMessage());
        assertTrue(e.getMessage().contains("FINNHUB_API_KEY") && e.getMessage().contains("ALPHAVANTAGE_API_KEY"), e.getMessage());
        assertFalse(e.getMessage().contains("fake"), "no provider-specific leftovers");
    }

    @Test
    void unknownSymbolIsNullNotAPriceAndOutranksMissingKeys() {
        MultiSourceStockService m = new MultiSourceStockService(List.of(failing(Reason.NO_KEY), returning(null)));
        assertNull(m.getStock("ZZZ"));
        assertFalse(m.isValidStock("ZZZ"));
        PriceUnavailableException e = assertThrows(PriceUnavailableException.class, () -> m.getCurrentPrice("ZZZ"));
        assertEquals(Reason.SYMBOL_NOT_FOUND, e.getReason());
    }

    @Test
    void historyIsNeverSubstitutedWhenNoProviderHasIt() {
        MultiSourceStockService m = new MultiSourceStockService(List.of(returning(null), returning(null)));
        PriceUnavailableException e = assertThrows(PriceUnavailableException.class, () -> m.getHistoricalData("A"));
        assertEquals(Reason.UNSUPPORTED, e.getReason());
    }

    @Test
    void defaultUpdateStockPriceLeavesTheStockUntouchedOnFailure() {
        Stock held = new Stock("A", "A", 10.0);
        assertThrows(PriceUnavailableException.class, () -> failing(Reason.NETWORK).updateStockPrice(held));
        assertEquals(10.0, held.getCurrentPrice(), 0.0);
        Stock live = new Stock("A", "A", 11.0);
        live.setChangePercent(1.5);
        returning(live).updateStockPrice(held);
        assertEquals(11.0, held.getCurrentPrice(), 0.0);
        assertEquals(1.5, held.getChangePercent(), 0.0);
    }

    // ---- history parsing

    @Test
    void parsesTwelveDataHistoryOldestFirstAndSkipsUnusableRows() {
        String json = "{\"meta\":{},\"values\":[{\"datetime\":\"2026-01-03\",\"close\":\"30.5\"},{\"datetime\":\"2026-01-02\",\"close\":\"bad\"},"
                + "{\"datetime\":\"2026-01-01\",\"close\":\"10\"},{\"datetime\":\"2025-12-31\",\"close\":\"-4\"}],\"status\":\"ok\"}";
        List<PriceHistory.Point> pts = PriceHistory.parse(json);
        assertEquals(2, pts.size());
        assertEquals(LocalDate.of(2026, 1, 1), pts.get(0).date());
        assertEquals(30.5, pts.get(1).close(), 0.0);
    }

    @Test
    void parsesAlphaVantageDailySeries() {
        String json = "{\"Time Series (Daily)\":{\"2026-02-02\":{\"4. close\":\"21.0\"},\"2026-02-01\":{\"4. close\":\"20.0\"}}}";
        List<PriceHistory.Point> pts = PriceHistory.parse(json);
        assertEquals(List.of(20.0, 21.0), pts.stream().map(PriceHistory.Point::close).toList());
        assertEquals(1, PriceHistory.lastDays(pts, 1).size());
        assertEquals(21.0, PriceHistory.lastDays(pts, 1).get(0).close(), 0.0);
    }

    @Test
    void historyWithNoUsableRowsIsAnErrorNotAnEmptyOrInventedSeries() {
        assertThrows(PriceUnavailableException.class, () -> PriceHistory.parse("not json"));
        assertThrows(PriceUnavailableException.class, () -> PriceHistory.parse("{}"));
        assertThrows(PriceUnavailableException.class, () -> PriceHistory.parse("{\"values\":[{\"datetime\":\"2026-01-01\",\"close\":\"0\"}]}"));
    }

    // ---- Alpha Vantage additions

    @Test
    void alphaVantageHistoryRequiresAKeyAndRejectsProviderMessages() {
        assertEquals(Reason.NO_KEY, assertThrows(PriceUnavailableException.class, () -> new AlphaVantageService(null).getHistoricalData("A")).getReason());
        assertEquals(Reason.RATE_LIMIT, assertThrows(PriceUnavailableException.class,
                () -> AlphaVantageService.requireNoProviderMessage("{\"Note\":\"Thank you for using Alpha Vantage\"}")).getReason());
        assertEquals(Reason.RATE_LIMIT, assertThrows(PriceUnavailableException.class,
                () -> AlphaVantageService.requireNoProviderMessage("{\"Information\":\"standard API rate limit is 25 requests per day\"}")).getReason());
        assertEquals(Reason.SYMBOL_NOT_FOUND, assertThrows(PriceUnavailableException.class,
                () -> AlphaVantageService.requireNoProviderMessage("{\"Error Message\":\"Invalid API call\"}")).getReason());
        assertDoesNotThrow(() -> AlphaVantageService.requireNoProviderMessage("{\"Time Series (Daily)\":{}}"));
    }

    @Test
    void alphaVantageQuoteCarriesTheReportedDailyChange() {
        Stock s = AlphaVantageService.parseGlobalQuote("A", "{\"Global Quote\":{\"05. price\":\"101.25\",\"10. change percent\":\"-1.2500%\"}}");
        assertEquals(101.25, s.getCurrentPrice(), 0.0);
        assertEquals(-1.25, s.getChangePercent(), 0.0);
        Stock noChange = AlphaVantageService.parseGlobalQuote("A", "{\"Global Quote\":{\"05. price\":\"5\"}}");
        assertEquals(0.0, noChange.getChangePercent(), 0.0);
    }

    // ---- key resolution

    @Test
    void keysComeFromTheIgnoredLocalFileAndNeverHaveBuiltInValues() throws Exception {
        assertEquals("", ApiKeyManager.get("STOCKVAULT_TEST_ONLY_KEY"), "unset means empty, not a placeholder");
        assertFalse(ApiKeyManager.isConfigured(ApiKeyManager.get("STOCKVAULT_TEST_ONLY_KEY")));

        Path cfg = tmp.resolve("local.properties");
        Files.writeString(cfg, "STOCKVAULT_TEST_ONLY_KEY = from-file\nFINNHUB_API_KEY=k1, k2\n");
        System.setProperty("stockvault.config", cfg.toString());
        ApiKeyManager.resetForTests();
        assertEquals("from-file", ApiKeyManager.get("STOCKVAULT_TEST_ONLY_KEY"));
        assertEquals("k1", ApiKeyManager.getFinnhubKey());
        assertEquals("k2", ApiKeyManager.getFinnhubKey(), "round-robin over the comma-separated list");
        assertEquals("k1", ApiKeyManager.getFinnhubKey());
    }
}
