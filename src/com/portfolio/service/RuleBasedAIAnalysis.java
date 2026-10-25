package com.portfolio.service;

import com.portfolio.model.Stock;
import com.portfolio.model.PortfolioItem;
import java.util.*;

/**
 * Rule-based AI Analysis system for stock evaluation.
 * Evaluates Trend, Momentum, Volatility, and Diversification impact.
 */
public class RuleBasedAIAnalysis {
    private final PortfolioService portfolioService;

    public RuleBasedAIAnalysis(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    public static class AnalysisResult {
        public int totalScore;
        public String recommendation;
        public String insight;
        public String trend;
        public String momentum;
        public String volatility;

        @Override
        public String toString() {
            return String.format("Score: %d/10 | Rec: %s%n%s", totalScore, recommendation, insight);
        }
    }

    /**
     * Analyzes a stock based on real historical data.
     */
    public AnalysisResult analyzeStock(Stock stock) {
        AnalysisResult result = new AnalysisResult();
        int score = 0;

        try {
            String historicalData = portfolioService.getPriceService().getHistoricalData(stock.getSymbol());
            List<Double> prices = parseHistoricalPrices(historicalData);

            if (prices.size() >= 50) {
                double currentPrice = prices.get(0);
                double sma20 = calculateSMA(prices, 20);
                double sma50 = calculateSMA(prices, 50);
                double ema20 = calculateEMA(prices, 20);

                // 1. Trend Logic: SMA 20 > SMA 50 = Uptrend
                if (sma20 > sma50) {
                    score += 3;
                    result.trend = "Uptrend (Bullish)";
                } else {
                    result.trend = "Downtrend (Bearish)";
                }

                // 2. Momentum Logic: EMA 20 vs Price
                double momentum30 = ((prices.get(0) - prices.get(Math.min(30, prices.size() - 1)))
                        / prices.get(Math.min(30, prices.size() - 1))) * 100;

                if (currentPrice > ema20 && momentum30 > 5.0) {
                    score += 2;
                    result.momentum = "Strong";
                } else if (momentum30 >= 0) {
                    result.momentum = "Moderate";
                } else {
                    result.momentum = "Weak";
                }

                // 3. Volatility Logic
                double volatility = calculateVolatility(prices, 20);
                if (volatility > 4.0) {
                    result.volatility = "High Risk";
                } else if (volatility > 1.5) {
                    score += 2;
                    result.volatility = "Moderate (Balanced)";
                } else {
                    result.volatility = "Low (Stable)";
                }
            } else {
                if (prices.size() >= 20) {
                    double sma20 = calculateSMA(prices, 20);
                    double current = prices.get(0);
                    result.trend = current > sma20 ? "Short-term Uptrend" : "Short-term Downtrend";
                    result.momentum = "Building";
                    result.volatility = "Moderate";
                    score += 4;
                } else {
                    result.trend = "Insufficient History";
                    result.momentum = "Neutral";
                    result.volatility = "Unknown";
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ AI Analysis failed for " + stock.getSymbol() + ": " + e.getMessage());
            result.trend = "Data Error";
            result.momentum = "N/A";
            result.volatility = "N/A";
        }

        // 4. Portfolio Diversification Check
        boolean improvesDiversification = checkDiversificationImpact(stock);
        if (improvesDiversification) {
            score += 3; // a holding that reduces concentration raises the score
        }

        result.totalScore = score;

        // Interpretation
        if (score >= 8) {
            result.recommendation = "Strong Buy";
        } else if (score >= 5) {
            result.recommendation = "Consider";
        } else {
            result.recommendation = "Avoid";
        }

        // Generate Insight
        result.insight = String.format(
                "The stock %s based on technical triggers. " +
                        "Momentum is %s and volatility is %s. " +
                        "This investment %s your portfolio diversification.",
                result.trend.equalsIgnoreCase("Uptrend (Bullish)") ? "shows a healthy bullish setup"
                        : "is currently underperforming",
                result.momentum.toLowerCase(),
                result.volatility.toLowerCase(),
                improvesDiversification ? "improves" : "may not improve");

        return result;
    }

    private List<Double> parseHistoricalPrices(String json) {
        List<Double> prices = new ArrayList<>();
        try {
            int timeSeriesIdx = json.indexOf("\"Time Series (Daily)\"");
            if (timeSeriesIdx == -1)
                return prices;

            int searchFrom = timeSeriesIdx;
            while (prices.size() < 60) {
                int closeIdx = json.indexOf("\"4. close\":", searchFrom);
                if (closeIdx == -1)
                    break;

                int startQuote = json.indexOf("\"", closeIdx + 11);
                int endQuote = json.indexOf("\"", startQuote + 1);
                String priceStr = json.substring(startQuote + 1, endQuote);
                prices.add(Double.parseDouble(priceStr));

                searchFrom = endQuote + 1;
            }
        } catch (Exception e) {
            System.err.println("Error parsing historical prices: " + e.getMessage());
        }
        return prices;
    }

    private double calculateSMA(List<Double> prices, int period) {
        double sum = 0;
        int count = Math.min(period, prices.size());
        for (int i = 0; i < count; i++) {
            sum += prices.get(i);
        }
        return sum / count;
    }

    private double calculateEMA(List<Double> prices, int period) {
        if (prices.isEmpty())
            return 0;
        double multiplier = 2.0 / (period + 1);
        double ema = calculateSMA(prices, period);

        int start = Math.min(period, prices.size() - 1);
        for (int i = start; i >= 0; i--) {
            ema = (prices.get(i) - ema) * multiplier + ema;
        }
        return ema;
    }

    private double calculateVolatility(List<Double> prices, int period) {
        if (prices.size() < 2)
            return 0;
        double sumChanges = 0;
        int count = Math.min(period, prices.size() - 1);
        for (int i = 0; i < count; i++) {
            double change = Math.abs((prices.get(i) - prices.get(i + 1)) / prices.get(i + 1)) * 100;
            sumChanges += change;
        }
        return sumChanges / count;
    }

    private boolean checkDiversificationImpact(Stock stock) {
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        if (items.isEmpty())
            return true;

        Map<String, Double> sectorWeights = new HashMap<>();
        double totalVal = 0;

        for (PortfolioItem item : items) {
            String s = item.getStock().getSector();
            double val = item.getTotalValue();
            sectorWeights.put(s, sectorWeights.getOrDefault(s, 0.0) + val);
            totalVal += val;
        }

        for (double weight : sectorWeights.values()) {
            if (totalVal > 0 && (weight / totalVal) > 0.80) {
                // If current portfolio is over-concentrated, check if this stock belongs to a
                // DIFFERENT sector
                return !sectorWeights.containsKey(stock.getSector());
            }
        }

        return true; // Not over-concentrated
    }
}
