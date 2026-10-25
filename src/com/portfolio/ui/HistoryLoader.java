package com.portfolio.ui;

import com.portfolio.service.PortfolioService;
import com.portfolio.service.PriceHistory;
import com.portfolio.service.PriceUnavailableException;

import javax.swing.SwingWorker;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Loads real daily price history in the background and hands it to the UI on the EDT. On failure the UI gets the
 * reason; charts must show "unavailable" with that reason, never generated data.
 */
final class HistoryLoader {
    private HistoryLoader() {
    }

    static void load(PortfolioService service, String symbol, Consumer<List<PriceHistory.Point>> onLoaded,
            Consumer<String> onFailed) {
        new SwingWorker<List<PriceHistory.Point>, Void>() {
            @Override
            protected List<PriceHistory.Point> doInBackground() {
                return PriceHistory.parse(service.getPriceService().getHistoricalData(symbol));
            }

            @Override
            protected void done() {
                try {
                    onLoaded.accept(get());
                } catch (ExecutionException e) {
                    Throwable c = e.getCause();
                    onFailed.accept(c instanceof PriceUnavailableException ? c.getMessage() : String.valueOf(c));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }
}
