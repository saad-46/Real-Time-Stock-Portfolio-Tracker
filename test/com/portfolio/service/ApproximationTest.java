package com.portfolio.service;

import com.portfolio.database.DatabaseManager;
import com.portfolio.model.PortfolioItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** Display-only conversions are flagged when they use fixed fallback rates, and validated totals are not. */
class ApproximationTest {
    @TempDir Path tmp;
    final TradePolicyTest.MutableClock clock = new TradePolicyTest.MutableClock();
    final TradePolicyTest.FakeRates rates = new TradePolicyTest.FakeRates(clock);
    final CurrencyService currency = new CurrencyService(rates, clock);

    @BeforeEach
    void setUp() {
        DatabaseManager.closeConnection();
        System.setProperty("portfolio.db.path", tmp.resolve("a.db").toString());
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.closeConnection();
        System.clearProperty("portfolio.db.path");
    }

    private PortfolioService serviceWithUsdHolding() {
        com.portfolio.TestWallet.fund(10_000, "USD"); // a dollar wallet, so buying a dollar holding needs no rate
        PortfolioService s = new PortfolioService(new TradePolicyTest.FakeProvider(), clock, currency);
        PortfolioItem i = new PortfolioItem("AAA", "AAA", 10, 10.0, 12.0);
        i.setOriginalCurrency("USD");
        s.buyStock(i);
        s.setBaseCurrency("INR"); // totals are displayed in rupees
        return s;
    }

    @Test
    void aTotalFromAValidatedLiveRateIsNotMarkedApproximate() {
        PortfolioService s = serviceWithUsdHolding();
        rates.rates.put("USD>INR", 80.0);
        PortfolioService.Valuation v = s.currentValueChecked();
        assertFalse(v.approximate());
        assertEquals("", v.mark());
        assertEquals(10 * 12.0 * 80.0, v.value(), 0.001); // 10 shares at a current price of 12, times the validated rate
        assertFalse(s.approximateRatesInUse());
        assertFalse(s.convertToBaseChecked(5, "USD").approximate());
    }

    @Test
    void aTotalThatNeededTheFixedFallbackIsMarkedAndSoIsTheBanner() {
        PortfolioService s = serviceWithUsdHolding();
        rates.failure = new IOException("down");
        PortfolioService.Valuation v = s.currentValueChecked();
        assertTrue(v.approximate());
        assertEquals("≈ ", v.mark());
        assertEquals(10 * 12.0 * 83.0, v.value(), 0.001, "fixed approximate rate");
        assertTrue(s.profitLossChecked().approximate());
        assertTrue(s.totalInvestmentChecked().approximate());
        assertTrue(s.approximateRatesInUse());
        assertTrue(s.convertToBaseChecked(5, "USD").approximate());
        assertEquals(s.currentValueChecked().value(), s.calculateCurrentValue(), 0.0, "the plain getters still work");
    }

    @Test
    void amountsAlreadyInTheDisplayCurrencyAreNeverApproximate() {
        PortfolioService s = serviceWithUsdHolding();
        rates.failure = new IOException("down");
        s.setBaseCurrency("USD");
        assertFalse(s.currentValueChecked().approximate());
        assertFalse(s.approximateRatesInUse() && s.convertToBaseChecked(1, "USD").approximate());
    }

    @Test
    void theApproximateStateClearsOnceLiveRatesWorkAgain() {
        PortfolioService s = serviceWithUsdHolding();
        rates.failure = new IOException("down");
        assertTrue(s.currentValueChecked().approximate());
        assertTrue(s.approximateRatesInUse());

        rates.failure = null;
        rates.rates.put("USD>INR", 81.0);
        clock.advance(CurrencyService.FAILURE_MEMORY.plusSeconds(1)); // the failed lookup is remembered briefly, then retried
        assertFalse(s.currentValueChecked().approximate(), "a validated rate is used again once the source is retried");
        assertTrue(s.approximateRatesInUse(), "the banner lingers briefly after the last approximation");
        clock.advance(CurrencyService.APPROXIMATION_WINDOW.plusSeconds(1));
        assertFalse(s.approximateRatesInUse());
    }

    @Test
    void approximationNeverLeaksIntoMoneyMovement() {
        PortfolioService s = serviceWithUsdHolding();
        com.portfolio.TestWallet.fund(0.01, "USD"); // keep the wallet funded; currency already USD
        rates.failure = new IOException("down");
        assertTrue(s.currentValueChecked().approximate()); // display falls back...
        // ...but a trade that needs a rate is still blocked
        PortfolioItem inr = new PortfolioItem("BBB", "BBB", 1, 100.0, 100.0);
        inr.setOriginalCurrency("INR");
        TradeBlockedException e = assertThrows(TradeBlockedException.class, () -> s.buyStock(inr));
        assertEquals(TradeBlockedException.Reason.RATE_UNAVAILABLE, e.getReason());
        assertEquals(1, s.getAllPortfolioItems().size());
    }

    @Test
    void validatedRateWindowIsOneHourAndApproximationWindowFiveMinutes() {
        assertEquals(Duration.ofHours(1), CurrencyService.RATE_MAX_AGE);
        assertEquals(Duration.ofMinutes(5), CurrencyService.APPROXIMATION_WINDOW);
    }
}
