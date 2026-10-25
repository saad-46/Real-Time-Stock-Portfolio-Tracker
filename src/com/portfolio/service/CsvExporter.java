package com.portfolio.service;

import com.portfolio.model.PortfolioItem;
import com.portfolio.model.Transaction;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CSV export (RFC 4180 quoting, UTF-8, CRLF). Text cells that start with = + - @ are prefixed
 * with an apostrophe so spreadsheet programs do not execute them as formulas.
 * Numbers are written as plain decimals, never through this text guard.
 */
public final class CsvExporter {
    private static final String EOL = "\r\n";
    // Always prints seconds (LocalDateTime.toString() drops ":00" seconds)
    private static final java.time.format.DateTimeFormatter TS =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private CsvExporter() {
    }

    public static String transactionsCsv(List<Transaction> txs) {
        StringBuilder sb = new StringBuilder("timestamp,symbol,type,quantity,price,realized_pl").append(EOL);
        for (Transaction t : txs) {
            sb.append(text(t.getTimestamp().format(TS))).append(',')
                    .append(text(t.getSymbol())).append(',')
                    .append(text(t.getType())).append(',')
                    .append(t.getQuantity()).append(',')
                    .append(money(t.getPrice())).append(',')
                    .append(t.getRealizedPl() == null ? "" : money(t.getRealizedPl()))
                    .append(EOL);
        }
        return sb.toString();
    }

    public static String holdingsCsv(List<PortfolioItem> items) {
        StringBuilder sb = new StringBuilder("symbol,name,quantity,avg_cost,current_price,price_updated_at").append(EOL);
        for (PortfolioItem i : items) {
            sb.append(text(i.getSymbol())).append(',')
                    .append(text(i.getName())).append(',')
                    .append(i.getQuantity()).append(',')
                    .append(BigDecimal.valueOf(i.getPurchasePrice()).setScale(4, RoundingMode.HALF_UP).toPlainString()).append(',')
                    .append(money(i.getCurrentPrice())).append(',')
                    .append(i.getPriceUpdatedAt() == null ? "" : i.getPriceUpdatedAt().toString())
                    .append(EOL);
        }
        return sb.toString();
    }

    /** Writes UTF-8 CSV to {@code target}, replacing it if it exists (callers must confirm overwrite with the user). */
    public static void write(Path target, String csv) throws IOException {
        Files.writeString(target, csv, StandardCharsets.UTF_8);
    }

    static String money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static String text(String s) {
        if (s == null) {
            return "";
        }
        String v = s;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
