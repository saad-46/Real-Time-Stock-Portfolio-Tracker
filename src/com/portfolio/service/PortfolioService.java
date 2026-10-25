package com.portfolio.service;

import com.portfolio.database.PortfolioDAO;
import com.portfolio.model.PortfolioItem;
import com.portfolio.model.PriceAlert;
import com.portfolio.model.Stock;
import com.portfolio.model.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * PortfolioService handles all business logic for the portfolio: holdings, trades, the wallet that pays for them,
 * the watchlist, price alerts, price refresh and analytics.
 *
 * Every trade (buy or sell) is ONE database transaction covering the wallet, the holding and the ledger
 * (see PortfolioDAO.recordTrade and docs/INTEGRATION_DESIGN.md). In-memory state changes only after the commit, so any
 * failure leaves memory and database exactly as they were.
 *
 * Prices are never invented: when a live price cannot be obtained the last known price is kept (and marked stale by
 * its priceUpdatedAt timestamp), and a sale is refused unless a validated live quote was obtained.
 */
public class PortfolioService {
    private List<PortfolioItem> portfolioItems = new ArrayList<>();
    private List<Transaction> transactions = new ArrayList<>(); // oldest first
    private List<Stock> watchlist = new ArrayList<>();
    private final StockPriceService priceService;
    private final PortfolioDAO portfolioDAO;
    private final CurrencyService currencyService;
    private String baseCurrency = "INR"; // Default display currency
    private final NotificationManager notificationManager;
    private final List<PriceAlert> priceAlerts = new ArrayList<>();
    private final WalletService walletService;
    private final java.time.Clock clock;
    private volatile String lastRefreshProblem;
    private volatile String lastTradeProblem;
    // Guards against overlapping refreshes (repeated clicks)
    private final java.util.concurrent.atomic.AtomicBoolean refreshInProgress = new java.util.concurrent.atomic.AtomicBoolean(false);

    /** Uses the multi-provider failover chain (Finnhub → Twelve Data → Alpha Vantage). */
    public PortfolioService() {
        this(new MultiSourceStockService());
    }

    public PortfolioService(StockPriceService priceService) {
        this(priceService, java.time.Clock.systemDefaultZone());
    }

    /** Clock is injectable so tests can force identical timestamps. */
    public PortfolioService(StockPriceService priceService, java.time.Clock clock) {
        this(priceService, clock, new CurrencyService());
    }

    /** Clock and exchange-rate service are injectable so tests can control time and rates. */
    public PortfolioService(StockPriceService priceService, java.time.Clock clock, CurrencyService currencyService) {
        this.clock = clock;
        this.priceService = priceService;
        this.portfolioDAO = new PortfolioDAO(); // throws IllegalStateException if the database is unusable
        this.currencyService = currencyService;
        this.notificationManager = NotificationManager.getInstance();
        this.walletService = new WalletService();

        // Load existing data from database
        loadFromDatabase();
    }

    /**
     * Load all portfolio items, transactions and the watchlist from the database.
     * A failed load is reported, never presented as an empty portfolio.
     */
    private synchronized void loadFromDatabase() {
        try {
            this.portfolioItems = portfolioDAO.loadAllPortfolioItems();
            System.out.println("✅ Loaded " + portfolioItems.size() + " stocks from database");

            this.transactions = portfolioDAO.loadAllTransactions();
            System.out.println("✅ Loaded " + transactions.size() + " transactions from database");

            this.watchlist = portfolioDAO.loadWatchlist();
            System.out.println("✅ Loaded " + watchlist.size() + " watchlist items from database");
        } catch (RuntimeException e) {
            throw new IllegalStateException("Could not load portfolio data from the database: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ watchlist

    public synchronized List<Stock> getWatchlist() {
        return new ArrayList<>(watchlist);
    }

    public synchronized boolean addToWatchlist(String symbol, String name) {
        boolean exists = watchlist.stream().anyMatch(s -> s.getSymbol().equalsIgnoreCase(symbol));
        if (exists) {
            return false;
        }
        try {
            portfolioDAO.addToWatchlist(symbol, name);
            watchlist.add(new Stock(symbol, name));
            System.out.println("⭐ Added to watchlist: " + symbol);
            return true;
        } catch (RuntimeException e) {
            System.err.println("❌ Error adding to watchlist: " + e.getMessage());
            return false;
        }
    }

    public synchronized void removeFromWatchlist(String symbol) {
        try {
            portfolioDAO.removeFromWatchlist(symbol);
            watchlist.removeIf(s -> s.getSymbol().equalsIgnoreCase(symbol));
            System.out.println("🗑️ Removed from watchlist: " + symbol);
        } catch (RuntimeException e) {
            System.err.println("❌ Error removing from watchlist: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ trading

    /** quantity x price, rounded to cents, in the currency the price is expressed in. */
    private static double money(int quantity, double price) {
        return BigDecimal.valueOf(quantity).multiply(BigDecimal.valueOf(price)).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static void requirePositivePrice(double price, String what) {
        if (!(price > 0) || Double.isInfinite(price)) {
            throw new IllegalArgumentException(what + " must be positive");
        }
    }

    /** What a trade moves in the wallet, in the wallet's own currency, and how that figure was arrived at. */
    private static final class Charge {
        final double walletAmount;
        final String note;

        Charge(double walletAmount, String note) {
            this.walletAmount = walletAmount;
            this.note = note;
        }
    }

    /**
     * Express {@code quantity x price} (in {@code currency}) in the wallet's currency. Same currency: no conversion.
     * Otherwise a validated, fresh exchange rate is required; without one the trade is blocked before anything is written.
     * A number in one currency is never debited from a wallet in another.
     */
    private Charge toWallet(String currency, int quantity, double price) {
        double inTradeCurrency = money(quantity, price);
        walletService.reload(); // the database is the source of truth for the wallet's currency
        String wallet = walletService.getCurrency();
        if (wallet.equalsIgnoreCase(currency)) {
            return new Charge(inTradeCurrency, String.format("%.2f %s", inTradeCurrency, wallet));
        }
        CurrencyService.Rate rate = currencyService.requireRate(currency, wallet); // throws TradeBlockedException
        double inWallet = BigDecimal.valueOf(inTradeCurrency).multiply(BigDecimal.valueOf(rate.rate()))
                .setScale(2, RoundingMode.HALF_UP).doubleValue();
        return new Charge(inWallet, String.format("%.2f %s = %.2f %s at %s/%s %s (%s, %s)", inTradeCurrency, currency.toUpperCase(),
                inWallet, wallet, wallet, currency.toUpperCase(), rate.rate(), rate.source(), rate.fetchedAt()));
    }

    /** Copy of a holding (with its stock data) at a new quantity. */
    private static PortfolioItem copyOf(PortfolioItem src, int quantity, double purchasePrice) {
        Stock s = src.getStock();
        Stock copy = new Stock(s.getSymbol(), s.getName(), s.getCurrentPrice());
        copy.setChangePercent(s.getChangePercent());
        copy.setSector(s.getSector());
        copy.setMarketCap(s.getMarketCap());
        copy.setRiskLevel(s.getRiskLevel());
        copy.setHigh24h(s.getHigh24h());
        copy.setLow24h(s.getLow24h());
        copy.setCurrency(s.getCurrency());
        PortfolioItem p = new PortfolioItem(copy, quantity, purchasePrice, src.getOriginalCurrency());
        p.setId(src.getId());
        p.setPriceUpdatedAt(src.getPriceUpdatedAt());
        return p;
    }

    /**
     * Buy: debits the wallet, adds to (or creates) the holding using weighted-average cost, and records the
     * transaction, all in one database transaction. The item's price is in the item's currency
     * ({@link PortfolioItem#getOriginalCurrency()}); if that is not the wallet's currency a validated exchange rate is
     * needed to work out the debit.
     *
     * @throws InsufficientFundsException if the wallet cannot pay (nothing changes)
     * @throws TradeBlockedException if the holding is in another currency, or a needed exchange rate is unavailable
     * @throws IllegalArgumentException for a missing symbol, non-positive quantity or non-positive price
     * @throws IllegalStateException on a database failure (nothing changes)
     */
    public synchronized void buyStock(PortfolioItem item) {
        if (item == null || item.getSymbol() == null || item.getSymbol().isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        if (item.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        requirePositivePrice(item.getPurchasePrice(), "Purchase price");
        String symbol = item.getSymbol().trim().toUpperCase();
        item.setSymbol(symbol);
        String currency = item.getOriginalCurrency();
        PortfolioItem existing = find(symbol);
        if (existing != null && !existing.getOriginalCurrency().equalsIgnoreCase(currency)) {
            throw new TradeBlockedException(TradeBlockedException.Reason.CURRENCY_MISMATCH,
                    "You hold " + symbol + " in " + existing.getOriginalCurrency() + "; this purchase is in " + currency
                            + ". Costs in different currencies are never averaged together. Nothing was changed.");
        }
        PortfolioItem after;
        if (existing == null) {
            after = item;
        } else {
            try {
                PortfolioItem merged = CostBasis.merge(existing, item);
                // keep the existing (possibly live) current price, not the new purchase price
                after = copyOf(existing, merged.getQuantity(), merged.getPurchasePrice());
                if (item.getStock().getSector() != null && existing.getStock().getSector() == null) {
                    after.getStock().setSector(item.getStock().getSector());
                }
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Quantity too large");
            }
        }
        Charge cost = toWallet(currency, item.getQuantity(), item.getPurchasePrice());
        Transaction transaction = new Transaction(symbol, "BUY", item.getQuantity(), item.getPurchasePrice(), null,
                java.time.LocalDateTime.now(clock));
        try {
            portfolioDAO.recordTrade(symbol, after, transaction, PortfolioDAO.WalletLeg.debit(cost.walletAmount,
                    "Bought " + item.getQuantity() + " shares of " + symbol + " (" + cost.note + ")"));
        } finally {
            walletService.reload(); // the database decides the balance, success or not
        }
        if (existing != null) {
            portfolioItems.remove(existing);
        }
        portfolioItems.add(after);
        transactions.add(transaction);
        notifyTrade(symbol, item.getQuantity(), true);
    }

    /** Buy with full details (the dashboard's form). Same guarantees as {@link #buyStock(PortfolioItem)}. */
    public synchronized void buyStock(String symbol, String name, int quantity, double price, String currency,
            String sector, String marketCap, String riskLevel) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        String sym = symbol.trim().toUpperCase();
        Stock stock = new Stock(sym, name == null || name.isBlank() ? sym : name);
        stock.setCurrentPrice(price);
        stock.setSector(sector == null || sector.isEmpty() ? getSectorForSymbol(sym) : sector);
        stock.setMarketCap(marketCap != null ? marketCap : "Mid Cap");
        stock.setRiskLevel(riskLevel != null ? riskLevel : "Medium");
        buyStock(new PortfolioItem(stock, quantity, price, currency));
    }

    public void buyStock(String symbol, String name, int quantity, double price) {
        buyStock(symbol, name, quantity, price, baseCurrency, null, null, null);
    }

    /** Simplified buy: just symbol, quantity and price. Example: buy("AAPL", 10, 150.0) */
    public void buy(String symbol, int quantity, double price) {
        buyStock(symbol, symbol, quantity, price);
    }

    /**
     * Sell {@code quantity} shares at a price the CALLER asserts (in the holding's currency): credits the wallet,
     * reduces (or removes) the holding and records the transaction with realized P/L from the holding's weighted-average
     * cost, in one database transaction. Interactive screens use {@link #sellStock(String, int, Quote)} instead, which
     * checks a live quote's freshness and currency; this form is for explicit user-entered prices and tests.
     *
     * @return realized profit/loss, rounded to cents, in the holding's currency
     * @throws IllegalArgumentException for a bad symbol/quantity/price, an unknown holding, or more shares than held
     * @throws TradeBlockedException if the proceeds need an exchange rate that is unavailable
     * @throws IllegalStateException on a database failure (nothing changes)
     */
    public synchronized BigDecimal sellStock(String symbol, int quantity, double price) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        requirePositivePrice(price, "Sell price");
        String sym = symbol.trim().toUpperCase();
        PortfolioItem existing = find(sym);
        if (existing == null) {
            throw new IllegalArgumentException("No holding for " + sym);
        }
        if (quantity > existing.getQuantity()) {
            throw new IllegalArgumentException("Cannot sell " + quantity + " shares; only " + existing.getQuantity() + " held");
        }
        BigDecimal realized = CostBasis.realizedPl(existing.getPurchasePrice(), price, quantity);
        int remaining = existing.getQuantity() - quantity;
        PortfolioItem after = remaining == 0 ? null : copyOf(existing, remaining, existing.getPurchasePrice());
        Charge proceeds = toWallet(existing.getOriginalCurrency(), quantity, price);
        Transaction transaction = new Transaction(sym, "SELL", quantity, price, realized.doubleValue(),
                java.time.LocalDateTime.now(clock));
        try {
            portfolioDAO.recordTrade(sym, after, transaction, PortfolioDAO.WalletLeg.credit(proceeds.walletAmount,
                    "Sold " + quantity + " shares of " + sym + " (" + proceeds.note + ")"));
        } finally {
            walletService.reload();
        }
        portfolioItems.remove(existing);
        if (after != null) {
            portfolioItems.add(after);
        }
        transactions.add(transaction);
        notifyTrade(sym, quantity, false);
        return realized;
    }

    // ------------------------------------------------------------------ quote-based trading

    /**
     * Obtain a live quote to trade on: a positive, finite price that a provider actually reported, stated in a known
     * currency, stamped with the time it was obtained. There is no fallback to the last known or purchase price.
     *
     * @throws PriceUnavailableException if no valid live quote is available (the trade must be aborted)
     * @throws TradeBlockedException (QUOTE_CURRENCY_UNKNOWN) if the provider did not say what currency it is in
     */
    public Quote fetchValidatedQuote(String symbol) {
        Stock live = priceService.getStock(symbol);
        if (live == null) {
            throw new PriceUnavailableException(PriceUnavailableException.Reason.SYMBOL_NOT_FOUND,
                    "No live quote exists for " + symbol);
        }
        Quote quote = Quote.of(live, clock.instant());
        requireUsable(symbol, quote, false);
        return quote;
    }

    /** Validate a quote for the given symbol; when {@code checkAge} also enforce the trading freshness limit. */
    private void requireUsable(String symbol, Quote quote, boolean checkAge) {
        if (quote == null || !(quote.getPrice() > 0) || Double.isInfinite(quote.getPrice())) {
            throw new PriceUnavailableException(PriceUnavailableException.Reason.BAD_RESPONSE,
                    "The provider returned an invalid price for " + symbol);
        }
        if (quote.getSymbol() != null && symbol != null && !quote.getSymbol().equalsIgnoreCase(symbol.trim())) {
            throw new IllegalArgumentException("The quote is for " + quote.getSymbol() + ", not " + symbol);
        }
        if (quote.getCurrency() == null) {
            throw new TradeBlockedException(TradeBlockedException.Reason.QUOTE_CURRENCY_UNKNOWN,
                    "The provider did not say what currency the " + symbol + " price is in, so it cannot be traded on.");
        }
        if (checkAge) {
            QuotePolicy.requireTradable(quote, clock.instant());
        }
    }

    /**
     * Sell at a live quote. The service itself enforces: price valid, currency known and equal to the holding's
     * currency, and quote no older than {@link QuotePolicy#TRADE_MAX_AGE}.
     *
     * @return realized profit/loss in the holding's currency
     * @throws TradeBlockedException STALE_QUOTE, QUOTE_CURRENCY_UNKNOWN, CURRENCY_MISMATCH or RATE_UNAVAILABLE
     */
    public synchronized BigDecimal sellStock(String symbol, int quantity, Quote quote) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        requireUsable(symbol, quote, true);
        PortfolioItem existing = find(symbol.trim().toUpperCase());
        if (existing != null && !existing.getOriginalCurrency().equalsIgnoreCase(quote.getCurrency())) {
            throw new TradeBlockedException(TradeBlockedException.Reason.CURRENCY_MISMATCH,
                    "The live " + symbol.toUpperCase() + " price is in " + quote.getCurrency() + " but you hold it in "
                            + existing.getOriginalCurrency() + "; the price is not converted between currencies. Nothing was changed.");
        }
        return sellStock(symbol, quantity, quote.getPrice());
    }

    /**
     * Buy at a live quote, with the same checks as {@link #sellStock(String, int, Quote)}. The holding is recorded in the
     * quote's currency and its price is stamped with the quote's time.
     */
    public synchronized void buyStock(String symbol, int quantity, Quote quote) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        requireUsable(symbol, quote, true);
        String sym = symbol.trim().toUpperCase();
        Stock stock = new Stock(sym, sym, quote.getPrice());
        stock.setCurrency(quote.getCurrency());
        stock.setSector(getSectorForSymbol(sym));
        stock.setMarketCap("Mid Cap");
        stock.setRiskLevel("Medium");
        PortfolioItem item = new PortfolioItem(stock, quantity, quote.getPrice(), quote.getCurrency());
        item.setPriceUpdatedAt(quote.getFetchedAt());
        buyStock(item);
    }

    /**
     * Sell at a freshly fetched, validated live quote. Returns false (reason in {@link #getLastTradeProblem()}) if
     * there is no such holding, not enough shares, no valid quote, or the trade was blocked; nothing changes then.
     */
    public boolean sellStock(String symbol, int quantity) {
        lastTradeProblem = null;
        try {
            Quote quote = fetchValidatedQuote(symbol);
            sellStock(symbol, quantity, quote);
            return true;
        } catch (PriceUnavailableException | IllegalArgumentException | IllegalStateException e) {
            lastTradeProblem = e.getMessage();
            System.err.println("❌ Sale of " + symbol + " not done: " + e.getMessage());
            return false;
        }
    }

    /** Sell every share of a symbol at a validated live quote (see {@link #sellStock(String, int)}). */
    public boolean sellAllStock(String symbol) {
        PortfolioItem item;
        synchronized (this) {
            item = find(symbol == null ? "" : symbol.trim().toUpperCase());
        }
        if (item == null) {
            lastTradeProblem = "Stock " + symbol + " not found in portfolio";
            System.err.println("❌ " + lastTradeProblem);
            return false;
        }
        return sellStock(symbol, item.getQuantity());
    }

    /** Why the most recent {@link #sellStock(String, int)} returned false, or null. */
    public String getLastTradeProblem() {
        return lastTradeProblem;
    }

    /**
     * Human-readable wallet effect of trading {@code quantity} shares at {@code quote}, for a confirmation dialog:
     * the amount in the quote's currency and, if the wallet uses another currency, the validated rate and converted amount.
     *
     * @throws TradeBlockedException if the trade cannot be done (currency mismatch with an existing holding, no valid rate)
     */
    public synchronized String describeWalletEffect(String symbol, int quantity, Quote quote, boolean buy) {
        requireUsable(symbol, quote, false);
        PortfolioItem existing = find(symbol.trim().toUpperCase());
        if (!buy) { // an impossible sale is refused now, before the user is asked to confirm it
            if (existing == null) {
                throw new IllegalArgumentException("No holding for " + symbol.toUpperCase());
            }
            if (quantity > existing.getQuantity()) {
                throw new IllegalArgumentException("Cannot sell " + quantity + " shares; only " + existing.getQuantity() + " held");
            }
        }
        if (existing != null && !existing.getOriginalCurrency().equalsIgnoreCase(quote.getCurrency())) {
            throw new TradeBlockedException(TradeBlockedException.Reason.CURRENCY_MISMATCH,
                    "The live " + symbol.toUpperCase() + " price is in " + quote.getCurrency() + " but you hold it in "
                            + existing.getOriginalCurrency() + "; the price is not converted between currencies.");
        }
        Charge c = toWallet(quote.getCurrency(), quantity, quote.getPrice());
        return (buy ? "Wallet debit: " : "Wallet credit: ") + c.note + " (wallet currency " + walletService.getCurrency() + ").";
    }

    /** Display status of a holding's price under {@link QuotePolicy}: "Fresh (3 min ago)", "Stale (2 h ago)", "Unavailable (never refreshed)". */
    public String quoteStatusText(PortfolioItem item) {
        return QuotePolicy.describe(item.getPriceUpdatedAt(), clock.instant());
    }

    public QuotePolicy.Status quoteStatus(PortfolioItem item) {
        return QuotePolicy.displayStatus(item.getPriceUpdatedAt(), clock.instant());
    }

    private void notifyTrade(String symbol, int quantity, boolean buy) {
        try {
            notificationManager.showTradeConfirmation(symbol, quantity, buy);
        } catch (RuntimeException e) {
            // The trade is committed; a notification problem must never look like a failed trade.
            System.err.println("Could not show the trade notification: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ ledger

    /**
     * Ledger query. All filters are optional (null/blank = no filter): symbol (case-insensitive
     * substring), type ("BUY"/"SELL"), from/to (inclusive dates). Newest first.
     */
    public synchronized List<Transaction> searchTransactions(String symbolContains, String type,
                                                             java.time.LocalDate from, java.time.LocalDate to) {
        String sym = symbolContains == null ? "" : symbolContains.trim().toUpperCase();
        List<Transaction> out = new ArrayList<>();
        // Iterate newest-inserted first, then stable-sort by time: ties (equal clock reads, unsaved ids)
        // keep newest-first because transactions are only ever appended.
        for (int i = transactions.size() - 1; i >= 0; i--) {
            Transaction t = transactions.get(i);
            if (!sym.isEmpty() && !t.getSymbol().toUpperCase().contains(sym)) {
                continue;
            }
            if (type != null && !type.isBlank() && !type.equalsIgnoreCase(t.getType())) {
                continue;
            }
            java.time.LocalDate d = t.getTimestamp().toLocalDate();
            if ((from != null && d.isBefore(from)) || (to != null && d.isAfter(to))) {
                continue;
            }
            out.add(t);
        }
        out.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
        return out;
    }

    /** Total realized P/L, derived only from SELL transactions (never double-counted). */
    public synchronized BigDecimal getTotalRealizedProfitLoss() {
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction t : transactions) {
            if ("SELL".equals(t.getType()) && t.getRealizedPl() != null) {
                total = total.add(BigDecimal.valueOf(t.getRealizedPl()));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private PortfolioItem find(String symbol) {
        for (PortfolioItem i : portfolioItems) {
            if (i.getSymbol().equalsIgnoreCase(symbol)) {
                return i;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ price refresh

    /**
     * Update stock prices from the live provider. Holdings whose live price cannot be obtained
     * keep their last known price (never a made-up one). The watchlist is refreshed the same way.
     *
     * @return the number of holdings that could NOT be updated
     */
    public int updateAllPrices() {
        if (!refreshInProgress.compareAndSet(false, true)) {
            throw new IllegalStateException("A price refresh is already in progress");
        }
        try {
            currencyService.forgetFailures(); // the user asked to refresh: retry exchange rates too
            return doUpdateAllPrices();
        } finally {
            refreshInProgress.set(false);
        }
    }

    /** True while a refresh is running (used by the UI to disable the button). */
    public boolean isRefreshInProgress() {
        return refreshInProgress.get();
    }

    private int doUpdateAllPrices() {
        lastRefreshProblem = null;
        int failed = 0;
        boolean fatal = false;
        List<PortfolioItem> snapshot = getAllPortfolioItems();
        for (int i = 0; i < snapshot.size(); i++) {
            PortfolioItem item = snapshot.get(i);
            try {
                Stock stock = priceService.getStock(item.getSymbol());
                if (stock == null) {
                    failed++;
                    noteProblem(item.getSymbol() + ": symbol not found by the provider");
                    continue;
                }
                // The stored price must be in the holding's currency. A quote in another currency is converted at a
                // validated rate; an unknown quote currency or an unavailable rate leaves the old price in place.
                String quoteCurrency = stock.getCurrency();
                if (quoteCurrency == null) {
                    failed++;
                    noteProblem(item.getSymbol() + ": the provider did not state the quote currency; price not updated");
                    continue;
                }
                double newPrice = stock.getCurrentPrice();
                if (!quoteCurrency.equalsIgnoreCase(item.getOriginalCurrency())) {
                    try {
                        newPrice = newPrice * currencyService.requireRate(quoteCurrency, item.getOriginalCurrency()).rate();
                    } catch (TradeBlockedException e) {
                        failed++;
                        noteProblem(item.getSymbol() + ": quoted in " + quoteCurrency + " but held in " + item.getOriginalCurrency()
                                + "; " + e.getMessage());
                        continue;
                    }
                }
                synchronized (this) {
                    // Re-resolve under the lock: a trade may have replaced the holding while we were fetching.
                    PortfolioItem current = find(item.getSymbol());
                    if (current == null) {
                        continue;
                    }
                    java.time.Instant stamp = clock.instant();
                    portfolioDAO.updateStockPrice(current.getSymbol(), newPrice, stamp);
                    current.setCurrentPrice(newPrice);
                    current.getStock().setChangePercent(stock.getChangePercent());
                    if (stock.getHigh24h() > 0) {
                        current.getStock().setHigh24h(stock.getHigh24h());
                    }
                    if (stock.getLow24h() > 0) {
                        current.getStock().setLow24h(stock.getLow24h());
                    }
                    current.setPriceUpdatedAt(stamp);
                }
                checkPriceAlerts(item.getSymbol(), newPrice);
            } catch (PriceUnavailableException e) {
                failed++;
                noteProblem(e.getMessage());
                System.out.println("Could not update price for " + item.getSymbol() + ": " + e.getMessage());
                if (e.getReason().isBatchFatal()) {
                    // Missing/invalid key or quota exhausted: every further call would fail the same way.
                    failed += snapshot.size() - i - 1;
                    fatal = true;
                    break;
                }
            } catch (Exception e) {
                failed++;
                noteProblem(e.getMessage());
                System.out.println("Could not update price for " + item.getSymbol() + ": " + e.getMessage());
            }
        }
        if (!fatal) {
            updateWatchlistData();
        }
        return failed;
    }

    /** Refresh the watchlist's live prices. A symbol that cannot be refreshed keeps its last price. */
    public void updateWatchlistData() {
        for (Stock s : getWatchlist()) {
            try {
                Stock live = priceService.getStock(s.getSymbol());
                if (live == null) {
                    noteProblem(s.getSymbol() + ": symbol not found by the provider");
                    continue;
                }
                s.setCurrentPrice(live.getCurrentPrice());
                s.setCurrency(live.getCurrency());
                s.setChangePercent(live.getChangePercent());
                if (live.getHigh24h() > 0) {
                    s.setHigh24h(live.getHigh24h());
                }
                if (live.getLow24h() > 0) {
                    s.setLow24h(live.getLow24h());
                }
                checkPriceAlerts(s.getSymbol(), live.getCurrentPrice());
            } catch (PriceUnavailableException e) {
                noteProblem(e.getMessage());
                System.err.println("Failed to update watchlist " + s.getSymbol() + ": " + e.getMessage());
                if (e.getReason().isBatchFatal()) {
                    return;
                }
            }
        }
    }

    private void noteProblem(String message) {
        if (lastRefreshProblem == null) {
            lastRefreshProblem = message;
        }
    }

    /** Why the most recent refresh failed for the first holding that failed, or null if nothing failed. */
    public String getLastRefreshProblem() {
        return lastRefreshProblem;
    }

    // ------------------------------------------------------------------ totals (price currency, no conversion)

    /** Total portfolio value: sum of quantity x currentPrice. */
    public synchronized double getTotalPortfolioValue() {
        BigDecimal total = BigDecimal.ZERO;
        for (PortfolioItem item : portfolioItems) {
            total = total.add(BigDecimal.valueOf(item.getQuantity()).multiply(BigDecimal.valueOf(item.getCurrentPrice())));
        }
        return total.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** Total investment: sum of quantity x purchasePrice. */
    public synchronized double getTotalInvestment() {
        BigDecimal total = BigDecimal.ZERO;
        for (PortfolioItem item : portfolioItems) {
            total = total.add(BigDecimal.valueOf(item.getQuantity()).multiply(BigDecimal.valueOf(item.getPurchasePrice())));
        }
        return total.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** Total profit or loss: totalValue - totalInvestment. */
    public synchronized double getTotalProfitLoss() {
        return Math.round((getTotalPortfolioValue() - getTotalInvestment()) * 100.0) / 100.0;
    }

    /** Return percentage: (profitLoss / investment) x 100. */
    public synchronized double getReturnPercentage() {
        if (getTotalInvestment() == 0) return 0;
        return Math.round((getTotalProfitLoss() / getTotalInvestment()) * 10000.0) / 100.0;
    }

    // ------------------------------------------------------------------ accessors

    /** Snapshot of all holdings (one merged entry per symbol). */
    public synchronized List<PortfolioItem> getAllPortfolioItems() {
        return new ArrayList<>(portfolioItems);
    }

    /** Snapshot of all transactions, oldest first. */
    public synchronized List<Transaction> getAllTransactions() {
        return new ArrayList<>(transactions);
    }

    /** Snapshot of all holdings. Same as {@link #getAllPortfolioItems()}. */
    public List<PortfolioItem> getPortfolioItems() {
        return getAllPortfolioItems();
    }

    /** Holdings, one entry per symbol (holdings are always kept merged by weighted-average cost). */
    public List<PortfolioItem> getMergedPortfolioItems() {
        return getAllPortfolioItems();
    }

    /** Snapshot of all transactions, newest first. */
    public synchronized List<Transaction> getTransactions() {
        List<Transaction> out = new ArrayList<>(transactions);
        java.util.Collections.reverse(out);
        return out;
    }

    /**
     * Discard a holding record. This is NOT a sale: no transaction, proceeds, wallet credit or realized P/L is recorded.
     */
    public synchronized void removeStock(String symbol) {
        portfolioDAO.deletePortfolioItem(symbol);
        portfolioItems.removeIf(item -> item.getSymbol().equals(symbol));
    }

    /** Check if a stock is valid (exists at the provider). */
    public boolean isValidStock(String symbol) {
        return priceService.isValidStock(symbol);
    }

    public StockPriceService getPriceService() {
        return priceService;
    }

    public CurrencyService getCurrencyService() {
        return currencyService;
    }

    public WalletService getWalletService() {
        return walletService;
    }

    // ------------------------------------------------------------------ currency

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String currency) {
        this.baseCurrency = currency;
    }

    public double convertToBase(double amount, String fromCurrency) {
        return currencyService.convert(amount, fromCurrency, baseCurrency);
    }

    /** A total in the display currency and whether any part of it used approximate (non-validated) rates. */
    public record Valuation(double value, boolean approximate) {
        /** "≈ " when approximate, else "". */
        public String mark() {
            return approximate ? "≈ " : "";
        }
    }

    /** Total money invested, converted to the display currency, flagged if approximate. */
    public Valuation totalInvestmentChecked() {
        double total = 0;
        boolean approx = false;
        for (PortfolioItem item : getAllPortfolioItems()) {
            CurrencyService.Converted c = currencyService.convertChecked(item.getPurchasePrice() * item.getQuantity(),
                    item.getOriginalCurrency(), baseCurrency);
            total += c.value();
            approx |= c.approximate();
        }
        return new Valuation(total, approx);
    }

    /** Current total value, converted to the display currency, flagged if approximate. */
    public Valuation currentValueChecked() {
        double total = 0;
        boolean approx = false;
        for (PortfolioItem item : getAllPortfolioItems()) {
            CurrencyService.Converted c = currencyService.convertChecked(item.getTotalValue(), item.getOriginalCurrency(), baseCurrency);
            total += c.value();
            approx |= c.approximate();
        }
        return new Valuation(total, approx);
    }

    /** Current value minus investment, in the display currency, flagged if either part was approximate. */
    public Valuation profitLossChecked() {
        Valuation v = currentValueChecked();
        Valuation i = totalInvestmentChecked();
        return new Valuation(v.value() - i.value(), v.approximate() || i.approximate());
    }

    /** One amount converted to the display currency, flagged if approximate. */
    public CurrencyService.Converted convertToBaseChecked(double amount, String fromCurrency) {
        return currencyService.convertChecked(amount, fromCurrency, baseCurrency);
    }

    /** True while any displayed figure recently used approximate rates (clears when live rates work again). */
    public boolean approximateRatesInUse() {
        return currencyService.approximateRatesInUse();
    }

    /** Total money invested, converted to the base currency (see {@link #totalInvestmentChecked()} for the approximation flag). */
    public double calculateTotalInvestment() {
        return totalInvestmentChecked().value();
    }

    /** Current total value, converted to the base currency (see {@link #currentValueChecked()}). */
    public double calculateCurrentValue() {
        return currentValueChecked().value();
    }

    /** Current value minus investment, in the base currency (see {@link #profitLossChecked()}). */
    public double calculateProfitLoss() {
        return profitLossChecked().value();
    }

    // ------------------------------------------------------------------ console display

    public void displayPortfolio() {
        System.out.println("\n💼 Your Portfolio:");
        System.out.println("==================");

        double totalValue = 0;
        double totalGainLoss = 0;

        for (PortfolioItem item : getAllPortfolioItems()) {
            System.out.println(item);
            System.out.println("   Value: $" + String.format("%.2f", item.getTotalValue()));
            System.out.println("   Gain/Loss: $" + String.format("%.2f", item.getGainLoss()));
            System.out.println();

            totalValue += item.getTotalValue();
            totalGainLoss += item.getGainLoss();
        }

        System.out.println("==================");
        System.out.println("Total Portfolio Value: $" + String.format("%.2f", totalValue));
        System.out.println("Total Gain/Loss: $" + String.format("%.2f", totalGainLoss));
    }

    public void displayTransactions() {
        System.out.println("\n📜 Transaction History:");
        System.out.println("======================");
        for (Transaction transaction : getAllTransactions()) {
            System.out.println(transaction);
        }
    }

    // ------------------------------------------------------------------ classification and analytics

    private String getSectorForSymbol(String symbol) {
        symbol = symbol.toUpperCase();
        if (symbol.matches("AAPL|MSFT|GOOGL|NVDA|AMD|META|INFX"))
            return "IT & Tech";
        if (symbol.matches("JPM|GS|BAC|HDFC|ICICI|SBI"))
            return "Banking & Finance";
        if (symbol.matches("JNJ|PFE|UNH|SUNPHARMA"))
            return "Healthcare";
        if (symbol.matches("XOM|CVX|BP|RELIANCE"))
            return "Energy";
        if (symbol.matches("PG|KO|PEP|HINDUNILVR"))
            return "FMCG";
        if (symbol.matches("TSLA|F|GM|MARUTI|TATASTEEL"))
            return "Automobile & Manufacturing";
        return "Miscellaneous";
    }

    public List<PortfolioItem> getTopGainers(int limit) {
        List<PortfolioItem> items = getAllPortfolioItems();
        items.sort((a, b) -> Double.compare(b.getStock().getChangePercent(), a.getStock().getChangePercent()));
        return items.subList(0, Math.min(limit, items.size()));
    }

    public List<PortfolioItem> getTopLosers(int limit) {
        List<PortfolioItem> items = getAllPortfolioItems();
        items.sort((a, b) -> Double.compare(a.getStock().getChangePercent(), b.getStock().getChangePercent()));
        return items.subList(0, Math.min(limit, items.size()));
    }

    /**
     * ILLUSTRATIVE ONLY: uses a fixed assumed volatility (0.15) and risk-free rate (5%), not market data.
     * Do not present it as a measured Sharpe ratio.
     */
    public double calculateSharpeRatio() {
        List<PortfolioItem> items = getAllPortfolioItems();
        if (items.isEmpty())
            return 0;
        double totalReturn = 0;
        for (PortfolioItem item : items) {
            if (item.getPurchasePrice() <= 0) {
                continue;
            }
            totalReturn += (item.getStock().getCurrentPrice() - item.getPurchasePrice()) / item.getPurchasePrice();
        }
        double avgReturn = totalReturn / items.size();
        double assumedVolatility = 0.15;
        double riskFreeRate = 0.05;
        return (avgReturn - riskFreeRate) / assumedVolatility;
    }

    /**
     * ILLUSTRATIVE ONLY: betas are fixed per-sector assumptions (tech 1.3, banking 1.1, healthcare 0.8, else 1.0),
     * not measured against any index.
     */
    public double calculatePortfolioBeta() {
        List<PortfolioItem> items = getAllPortfolioItems();
        if (items.isEmpty())
            return 1.0;
        double weightedBeta = 0;
        double totalVal = 0;
        for (PortfolioItem item : items) {
            totalVal += item.getTotalValue();
        }
        if (totalVal <= 0) {
            return 1.0;
        }
        for (PortfolioItem item : items) {
            double beta = 1.0;
            String sector = item.getStock().getSector();
            if ("IT & Tech".equals(sector))
                beta = 1.3;
            else if ("Banking & Finance".equals(sector))
                beta = 1.1;
            else if ("Healthcare".equals(sector))
                beta = 0.8;

            weightedBeta += beta * (item.getTotalValue() / totalVal);
        }
        return weightedBeta;
    }

    // ------------------------------------------------------------------ alerts

    /** Record a live price for a holding or watchlist symbol and trigger any matching price alerts. */
    public synchronized void updateStockPrice(String symbol, double newPrice) {
        requirePositivePrice(newPrice, "Price");
        PortfolioItem held = find(symbol);
        if (held != null) {
            held.getStock().setCurrentPrice(newPrice);
        } else {
            for (Stock s : watchlist) {
                if (s.getSymbol().equalsIgnoreCase(symbol)) {
                    s.setCurrentPrice(newPrice);
                    break;
                }
            }
        }
        checkPriceAlerts(symbol, newPrice);
    }

    /** Snapshot of the price alerts (active and already triggered). */
    public synchronized List<PriceAlert> getPriceAlerts() {
        return new ArrayList<>(priceAlerts);
    }

    public synchronized void addPriceAlert(PriceAlert alert) {
        priceAlerts.add(alert);
        System.out.println("🔔 Price alert set for " + alert.getSymbol() + " @ ₹" + alert.getTargetPrice());
    }

    private synchronized void checkPriceAlerts(String symbol, double currentPrice) {
        for (PriceAlert alert : priceAlerts) {
            if (alert.getSymbol().equalsIgnoreCase(symbol) && !alert.isTriggered()) {
                if (alert.checkCondition(currentPrice)) {
                    alert.setTriggered(true);
                    notificationManager.showPriceAlert(symbol, alert.getTargetPrice(), currentPrice);
                    System.out.println("🚨 ALERT TRIGGERED: " + symbol + " reached " + currentPrice);
                }
            }
        }
    }
}
