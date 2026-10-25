package com.portfolio.ui;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceAgeTest {
    private static final Instant NOW = Instant.parse("2025-01-10T12:00:00Z");

    @Test
    void neverRefreshed() {
        assertEquals("Not refreshed", ModernPortfolioUI.priceAge(null, NOW));
    }

    @Test
    void minutesHoursAndStale() {
        assertEquals("5 min ago", ModernPortfolioUI.priceAge(NOW.minus(Duration.ofMinutes(5)), NOW));
        assertEquals("3 h ago", ModernPortfolioUI.priceAge(NOW.minus(Duration.ofHours(3)), NOW));
        assertEquals("23 h ago", ModernPortfolioUI.priceAge(NOW.minus(Duration.ofHours(23)), NOW));
        assertEquals("1 d ago (STALE)", ModernPortfolioUI.priceAge(NOW.minus(Duration.ofHours(24)), NOW));
        assertEquals("3 d ago (STALE)", ModernPortfolioUI.priceAge(NOW.minus(Duration.ofDays(3)), NOW));
    }

    @Test
    void futureTimestampIsTreatedAsJustNow() {
        assertEquals("0 min ago", ModernPortfolioUI.priceAge(NOW.plusSeconds(120), NOW));
    }
}
