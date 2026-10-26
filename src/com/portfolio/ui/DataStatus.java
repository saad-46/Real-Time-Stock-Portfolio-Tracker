package com.portfolio.ui;

import java.awt.Color;

/**
 * The four honest states of any figure or chart, shown with one compact, neutral treatment everywhere (see {@link StatusChip}).
 * Hiding a banner never changes what a value is: simulated data stays {@link #DEMO}, remembered data stays {@link #CACHED}.
 */
enum DataStatus {
    /** Returned by the data provider recently. */
    LIVE("Provider data", new Color(74, 222, 128)),
    /** Stored from an earlier request; may be out of date. */
    CACHED("Cached", new Color(245, 180, 80)),
    /** Simulated for demonstration; not market data. */
    DEMO("Demo data", new Color(139, 147, 184)),
    /** Your own records (wallet ledger, transactions). */
    LOCAL("Your records", new Color(102, 126, 234)),
    /** Nothing honest to show. */
    UNAVAILABLE("Unavailable", new Color(248, 113, 113));

    final String label;
    final Color dot;

    DataStatus(String label, Color dot) {
        this.label = label;
        this.dot = dot;
    }

    /** The text shown in the chip: the label plus an optional detail such as a time. */
    String text(String detail) {
        return detail == null || detail.isBlank() ? label : label + " · " + detail.trim();
    }

    /** Whether this status means the numbers are not real market data. */
    boolean simulated() {
        return this == DEMO;
    }
}
