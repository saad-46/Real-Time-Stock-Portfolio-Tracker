package com.portfolio.service;

import com.portfolio.model.Stock;
import com.portfolio.service.PriceUnavailableException.Reason;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * MultiSourceStockService - failover aggregator for stock price APIs.
 * Tries sources in order: Finnhub → Twelve Data → Alpha Vantage. If one provider fails or rate-limits, the next is
 * tried. The first provider that reports a real quote wins.
 *
 * It never invents a price: if no provider produced a quote the call fails with a PriceUnavailableException that
 * keeps the most informative reason (a missing key is reported only when nothing else went wrong), or returns null
 * when the providers that answered all said the symbol does not exist.
 */
public class MultiSourceStockService implements StockPriceService {

    private final FinnhubService finnhubService;
    private final TwelveDataService twelveDataService;
    private final AlphaVantageService alphaVantageService;
    private final List<StockPriceService> chain;

    public MultiSourceStockService() {
        this(new FinnhubService(), new TwelveDataService(), new AlphaVantageService());
    }

    /** Package-private: lets tests supply fake providers. */
    MultiSourceStockService(FinnhubService finnhub, TwelveDataService twelveData, AlphaVantageService alphaVantage) {
        this.finnhubService = finnhub;
        this.twelveDataService = twelveData;
        this.alphaVantageService = alphaVantage;
        this.chain = List.of(finnhub, twelveData, alphaVantage);
    }

    /** Failover over arbitrary providers (used by tests). */
    MultiSourceStockService(List<StockPriceService> providers) {
        this.finnhubService = null;
        this.twelveDataService = null;
        this.alphaVantageService = null;
        this.chain = List.copyOf(providers);
    }

    @Override
    public Stock getStock(String symbol) {
        return failover(p -> relabel(p.getStock(providerSymbol(p, symbol)), symbol), "price for " + symbol);
    }

    /** The symbol this provider understands for the listing, or an "unsupported" failure the failover moves past. */
    private String providerSymbol(StockPriceService provider, String symbol) {
        SymbolResolver.Provider kind = provider == alphaVantageService ? SymbolResolver.Provider.ALPHA_VANTAGE
                : provider == twelveDataService ? SymbolResolver.Provider.TWELVE_DATA
                : provider == finnhubService ? SymbolResolver.Provider.FINNHUB : null;
        if (kind == null) {
            return symbol; // a provider this class does not know (tests): the symbol as entered
        }
        SymbolResolver.Instrument instrument;
        try {
            instrument = SymbolResolver.parse(symbol);
        } catch (IllegalArgumentException e) {
            throw new PriceUnavailableException(Reason.SYMBOL_NOT_FOUND, e.getMessage());
        }
        return SymbolResolver.forProvider(instrument, kind).orElseThrow(() -> new PriceUnavailableException(Reason.UNSUPPORTED,
                displayName(kind) + " does not cover " + SymbolResolver.describe(instrument)));
    }

    private static String displayName(SymbolResolver.Provider kind) {
        return switch (kind) {
            case ALPHA_VANTAGE -> "Alpha Vantage";
            case TWELVE_DATA -> "Twelve Data";
            case FINNHUB -> "Finnhub";
        };
    }

    /** Quotes come back under the provider's spelling; the portfolio keeps the symbol it stored. */
    private static Stock relabel(Stock quote, String storedSymbol) {
        if (quote != null && !storedSymbol.equalsIgnoreCase(quote.getSymbol())) {
            quote.setSymbol(storedSymbol.trim().toUpperCase(java.util.Locale.ROOT));
        }
        return quote;
    }

    @Override
    public boolean isValidStock(String symbol) {
        return getStock(symbol) != null;
    }

    /** Daily history: the first provider that offers it. Failures are reported, never replaced by sample data. */
    @Override
    public String getHistoricalData(String symbol) {
        return getHistoricalData(symbol, 30);
    }

    @Override
    public String getHistoricalData(String symbol, int days) {
        // Twelve Data first (better history), then Alpha Vantage; Finnhub's free tier has none.
        List<StockPriceService> order = new ArrayList<>();
        if (twelveDataService != null) {
            order.add(twelveDataService);
            order.add(alphaVantageService);
        } else {
            order.addAll(chain);
        }
        PriceUnavailableException best = null;
        java.util.LinkedHashSet<String> reasons = new java.util.LinkedHashSet<>();
        for (StockPriceService p : order) {
            try {
                return p.getHistoricalData(providerSymbol(p, symbol), days);
            } catch (PriceUnavailableException e) {
                System.out.println("\u26a0\ufe0f " + p.getClass().getSimpleName() + " historical failed for " + symbol + ": " + e.getReason());
                if (e.getReason() != Reason.NO_KEY) {
                    reasons.add(withProvider(p, e.getMessage()));
                }
                if (best == null || rank(e.getReason()) > rank(best.getReason())) {
                    best = e;
                }
            }
        }
        if (best == null) {
            throw new PriceUnavailableException(Reason.UNSUPPORTED, "Historical data unavailable for " + symbol);
        }
        if (reasons.isEmpty()) {
            throw best; // every provider simply had no key
        }
        throw new PriceUnavailableException(best.getReason(), String.join("; ", reasons));
    }

    /** How actionable a failure is for the user: a quota or key problem beats "not in plan", which beats a missing key. */
    private static int rank(Reason r) {
        return switch (r) {
            case RATE_LIMIT -> 6;
            case INVALID_KEY -> 5;
            case NETWORK -> 4;
            case UNSUPPORTED, SYMBOL_NOT_FOUND -> 3;
            case NO_KEY -> 0;
            default -> 2;
        };
    }

    private String withProvider(StockPriceService p, String message) {
        String name = p == twelveDataService ? "Twelve Data" : p == alphaVantageService ? "Alpha Vantage"
                : p == finnhubService ? "Finnhub" : p.getClass().getSimpleName();
        String m = message == null ? "unavailable" : message;
        return m.startsWith(name) ? m : name + ": " + m;
    }

    /** Search for stocks. Uses Finnhub (broader coverage). */
    public String searchStocks(String query) {
        if (finnhubService == null) {
            throw new PriceUnavailableException(Reason.UNSUPPORTED, "Search is not available");
        }
        return finnhubService.searchStocks(query);
    }

    private Stock failover(Function<StockPriceService, Stock> call, String what) {
        PriceUnavailableException best = null;
        boolean someoneSaidNotFound = false;
        for (StockPriceService provider : chain) {
            try {
                Stock s = call.apply(provider);
                if (s != null) {
                    return s;
                }
                someoneSaidNotFound = true;
            } catch (PriceUnavailableException e) {
                // keep the most useful failure: anything but "no key" beats "no key"
                if (best == null || (best.getReason() == Reason.NO_KEY && e.getReason() != Reason.NO_KEY)) {
                    best = e;
                }
            }
        }
        // A provider that answered "no such symbol" is more informative than one that merely had no key.
        if (someoneSaidNotFound && (best == null || best.getReason() == Reason.NO_KEY)) {
            return null;
        }
        if (best != null && best.getReason() == Reason.NO_KEY) {
            // Every provider lacked a key: say so once, naming all the settings, instead of just the first provider's.
            throw new PriceUnavailableException(Reason.NO_KEY, "No API key configured for any price provider. Set "
                    + "FINNHUB_API_KEY, TWELVEDATA_API_KEY or ALPHAVANTAGE_API_KEY (environment variable or "
                    + ApiKeyManager.DEFAULT_CONFIG_FILE + ").");
        }
        if (best != null) {
            throw best;
        }
        throw new PriceUnavailableException(Reason.PROVIDER_MESSAGE, "No price provider is configured for " + what);
    }

    // Expose individual services for advanced use
    public FinnhubService getFinnhubService() {
        return finnhubService;
    }

    public TwelveDataService getTwelveDataService() {
        return twelveDataService;
    }

    public AlphaVantageService getAlphaVantageService() {
        return alphaVantageService;
    }
}
