package com.portfolio.service;

import com.portfolio.model.PortfolioItem;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Weighted-average cost-basis arithmetic (see docs/audit/TRADE_MODEL.md).
 * All math is BigDecimal; average cost is rounded to 4 dp, realized P/L to 2 dp, HALF_UP.
 */
public final class CostBasis {
    private CostBasis() {
    }

    /** Combine two holdings of the same symbol; keeps {@code a}'s id/name and the most recently loaded current price. */
    public static PortfolioItem merge(PortfolioItem a, PortfolioItem b) {
        int qty = Math.addExact(a.getQuantity(), b.getQuantity());
        double avg = weightedAverage(a.getQuantity(), a.getPurchasePrice(), b.getQuantity(), b.getPurchasePrice());
        PortfolioItem merged = new PortfolioItem(a.getId(), a.getSymbol(), a.getName(), qty, avg, b.getCurrentPrice());
        merged.setPriceUpdatedAt(b.getPriceUpdatedAt());
        merged.setOriginalCurrency(a.getOriginalCurrency());
        // Descriptive data survives the merge: a's classification wins, b supplies the freshest daily figures.
        com.portfolio.model.Stock out = merged.getStock();
        out.setSector(a.getStock().getSector() != null ? a.getStock().getSector() : b.getStock().getSector());
        out.setMarketCap(a.getStock().getMarketCap() != null ? a.getStock().getMarketCap() : b.getStock().getMarketCap());
        out.setRiskLevel(a.getStock().getRiskLevel() != null ? a.getStock().getRiskLevel() : b.getStock().getRiskLevel());
        out.setChangePercent(b.getStock().getChangePercent());
        out.setHigh24h(b.getStock().getHigh24h());
        out.setLow24h(b.getStock().getLow24h());
        return merged;
    }

    public static double weightedAverage(int q1, double c1, int q2, double c2) {
        BigDecimal cost = BigDecimal.valueOf(q1).multiply(BigDecimal.valueOf(c1))
                .add(BigDecimal.valueOf(q2).multiply(BigDecimal.valueOf(c2)));
        return cost.divide(BigDecimal.valueOf((long) q1 + q2), 4, RoundingMode.HALF_UP).doubleValue();
    }

    /** (sellPrice - avgCost) * quantity, rounded to cents. */
    public static BigDecimal realizedPl(double avgCost, double sellPrice, int quantity) {
        return BigDecimal.valueOf(sellPrice).subtract(BigDecimal.valueOf(avgCost))
                .multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }
}
