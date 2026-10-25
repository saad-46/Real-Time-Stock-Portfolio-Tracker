package com.portfolio.service;

import com.portfolio.model.PortfolioItem;
import com.portfolio.model.PriceAlert;
import com.portfolio.model.Stock;
import com.portfolio.model.Transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Everything the home screen shows, computed once from the service so the UI contains no business logic.
 *
 * Only real data appears: totals come from the holdings (and are flagged when they used approximate exchange rates);
 * quote status counts come from {@link QuotePolicy}; movers come only from holdings whose price was actually refreshed
 * from a provider; nothing is sampled or simulated. A holding that was never refreshed is counted as
 * {@code unavailable} and is valued at what was paid, which {@link #valuedAtCost()} reports.
 */
public final class PortfolioSummary {
    private final int holdings;
    private final PortfolioService.Valuation totalValue;
    private final PortfolioService.Valuation invested;
    private final PortfolioService.Valuation profitLoss;
    private final double returnPercent;
    private final String displayCurrency;
    private final double walletBalance;
    private final String walletCurrency;
    private final BigDecimal realizedPl;
    private final int fresh;
    private final int stale;
    private final int unavailable;
    private final List<Transaction> recent;
    private final List<Stock> watchlist;
    private final int activeAlerts;
    private final int triggeredAlerts;
    private final List<PortfolioItem> topGainers;
    private final List<PortfolioItem> topLosers;
    private final boolean approximate;
    private final String refreshProblem;

    private PortfolioSummary(PortfolioService s) {
        List<PortfolioItem> items = s.getAllPortfolioItems();
        holdings = items.size();
        totalValue = s.currentValueChecked();
        invested = s.totalInvestmentChecked();
        profitLoss = new PortfolioService.Valuation(totalValue.value() - invested.value(), totalValue.approximate() || invested.approximate());
        returnPercent = invested.value() > 0 ? profitLoss.value() / invested.value() * 100 : 0;
        displayCurrency = s.getBaseCurrency();
        walletBalance = s.getWalletService().getBalance();
        walletCurrency = s.getWalletService().getCurrency();
        realizedPl = s.getTotalRealizedProfitLoss();
        int f = 0, st = 0, un = 0;
        for (PortfolioItem i : items) {
            switch (s.quoteStatus(i)) {
                case FRESH -> f++;
                case STALE -> st++;
                default -> un++;
            }
        }
        fresh = f;
        stale = st;
        unavailable = un;
        List<Transaction> all = s.getTransactions(); // newest first
        recent = List.copyOf(all.subList(0, Math.min(5, all.size())));
        watchlist = List.copyOf(s.getWatchlist());
        int active = 0, triggered = 0;
        for (PriceAlert a : s.getPriceAlerts()) {
            if (a.isTriggered()) triggered++; else active++;
        }
        activeAlerts = active;
        triggeredAlerts = triggered;
        // movers: real provider-reported daily change, only for holdings whose price was refreshed
        List<PortfolioItem> real = new ArrayList<>();
        for (PortfolioItem i : items) {
            if (i.getPriceUpdatedAt() != null && i.getStock().getChangePercent() != 0) {
                real.add(i);
            }
        }
        List<PortfolioItem> up = new ArrayList<>(real.stream().filter(i -> i.getStock().getChangePercent() > 0)
                .sorted(Comparator.comparingDouble((PortfolioItem i) -> i.getStock().getChangePercent()).reversed()).toList());
        List<PortfolioItem> down = new ArrayList<>(real.stream().filter(i -> i.getStock().getChangePercent() < 0)
                .sorted(Comparator.comparingDouble((PortfolioItem i) -> i.getStock().getChangePercent())).toList());
        topGainers = List.copyOf(up.subList(0, Math.min(3, up.size())));
        topLosers = List.copyOf(down.subList(0, Math.min(3, down.size())));
        approximate = totalValue.approximate() || invested.approximate();
        refreshProblem = s.getLastRefreshProblem();
    }

    public static PortfolioSummary of(PortfolioService service) {
        return new PortfolioSummary(service);
    }

    public int holdings() { return holdings; }
    public PortfolioService.Valuation totalValue() { return totalValue; }
    public PortfolioService.Valuation invested() { return invested; }
    public PortfolioService.Valuation profitLoss() { return profitLoss; }
    public double returnPercent() { return returnPercent; }
    public String displayCurrency() { return displayCurrency; }
    public double walletBalance() { return walletBalance; }
    public String walletCurrency() { return walletCurrency; }
    public BigDecimal realizedPl() { return realizedPl; }
    public int fresh() { return fresh; }
    public int stale() { return stale; }
    public int unavailable() { return unavailable; }
    public List<Transaction> recent() { return recent; }
    public List<Stock> watchlist() { return watchlist; }
    public int activeAlerts() { return activeAlerts; }
    public int triggeredAlerts() { return triggeredAlerts; }
    public List<PortfolioItem> topGainers() { return topGainers; }
    public List<PortfolioItem> topLosers() { return topLosers; }
    /** True if any total used approximate (non-validated) exchange rates. */
    public boolean approximate() { return approximate; }
    public String refreshProblem() { return refreshProblem; }

    /** How many holdings are valued at the price paid because no live price was ever obtained. */
    public int valuedAtCost() { return unavailable; }

    /** Nothing to show yet: no holdings, no history, no money. */
    public boolean isEmpty() {
        return holdings == 0 && recent.isEmpty() && walletBalance == 0 && watchlist.isEmpty();
    }
}
