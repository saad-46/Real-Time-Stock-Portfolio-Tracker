package com.portfolio.ui;

import com.portfolio.service.PortfolioService;
import com.portfolio.service.PriceUnavailableException;
import com.portfolio.service.Quote;
import com.portfolio.service.QuotePolicy;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.math.BigDecimal;
import java.util.concurrent.ExecutionException;

/**
 * Trades that execute at a validated live quote: fetch the quote in the background, show the price, its currency, its
 * age and the exact effect on the wallet (including any currency conversion), and trade only if the user confirms, at
 * exactly the quote they saw. The trading service re-checks the quote's freshness and currency at execution, so a
 * confirmation dialog left open past {@link QuotePolicy#TRADE_MAX_AGE} fails safely. If no valid quote can be obtained,
 * or the money cannot be converted at a validated rate, the trade is aborted with the reason; there is no fallback price.
 */
final class TradeFlow {
    private TradeFlow() {
    }

    /** Sell {@code quantity} shares at a confirmed live quote. {@code onDone} runs on the EDT after a completed sale. */
    static void sell(Component parent, PortfolioService service, String symbol, int quantity, Runnable onDone) {
        run(parent, service, symbol, quantity, false, onDone);
    }

    /** Buy {@code quantity} shares at a confirmed live quote. {@code onDone} runs on the EDT after a completed purchase. */
    static void buy(Component parent, PortfolioService service, String symbol, int quantity, Runnable onDone) {
        run(parent, service, symbol, quantity, true, onDone);
    }

    /** What the user is asked to confirm: the quote and the text describing its wallet effect. */
    private record Offer(Quote quote, String walletEffect) {
    }

    private static void run(Component parent, PortfolioService service, String symbol, int quantity, boolean buy,
            Runnable onDone) {
        if (quantity <= 0) {
            JOptionPane.showMessageDialog(parent, "Quantity must be positive", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        new SwingWorker<Offer, Void>() {
            @Override
            protected Offer doInBackground() {
                Quote quote = service.fetchValidatedQuote(symbol);
                // also validates the exchange rate now, so a trade that cannot be priced is refused before confirmation
                return new Offer(quote, service.describeWalletEffect(symbol, quantity, quote, buy));
            }

            @Override
            protected void done() {
                String verb = buy ? "Buy" : "Sell";
                try {
                    Offer offer = get();
                    Quote q = offer.quote();
                    int answer = JOptionPane.showConfirmDialog(parent,
                            String.format("%s %d shares of %s at the live price %.2f %s?%nQuote obtained %s (valid for %d s).%n%s",
                                    verb, quantity, symbol, q.getPrice(), q.getCurrency(), q.getFetchedAt(),
                                    QuotePolicy.TRADE_MAX_AGE.toSeconds(), offer.walletEffect()),
                            "Confirm " + verb.toLowerCase(), JOptionPane.YES_NO_OPTION);
                    if (answer != JOptionPane.YES_OPTION) {
                        return;
                    }
                    String message;
                    if (buy) {
                        service.buyStock(symbol, quantity, q);
                        message = "Bought " + quantity + " shares of " + symbol + " at " + String.format("%.2f %s", q.getPrice(), q.getCurrency()) + ".";
                    } else {
                        BigDecimal realized = service.sellStock(symbol, quantity, q);
                        message = "Sold " + quantity + " shares of " + symbol + " at " + String.format("%.2f %s", q.getPrice(), q.getCurrency())
                                + ". Realized P/L: " + realized.toPlainString() + " " + q.getCurrency();
                    }
                    JOptionPane.showMessageDialog(parent, message, "Success", JOptionPane.INFORMATION_MESSAGE);
                    if (onDone != null) {
                        onDone.run();
                    }
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof PriceUnavailableException) {
                        JOptionPane.showMessageDialog(parent,
                                verb + " cancelled: no valid live price for " + symbol + " (" + cause.getMessage() + "). Nothing was changed.",
                                "No live price", JOptionPane.ERROR_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(parent, verb + " blocked: " + cause.getMessage() + "\nNothing was changed.",
                                "Trade blocked", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IllegalArgumentException | IllegalStateException e) {
                    // includes InsufficientFundsException, a stale quote, a currency mismatch; the trade did not happen
                    JOptionPane.showMessageDialog(parent, verb + " failed: " + e.getMessage() + "\nNothing was changed.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
