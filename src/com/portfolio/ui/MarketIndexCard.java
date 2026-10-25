package com.portfolio.ui;

import com.portfolio.service.MarketIndexService.Snapshot;
import com.portfolio.service.MarketIndexService.State;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * One market index: name, the latest provider-reported value (in index points), change, sparkline and an honest state line
 * (loading, provider and fetch time, stale, or unavailable with the actionable reason). It shows whatever {@link Snapshot} it is
 * given and nothing else. Clicking it (or Enter/Space when focused) asks the host to show this index in the featured chart.
 */
public class MarketIndexCard extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter AT = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final JLabel name = Theme.label("", Theme.font(java.awt.Font.BOLD, 11f), LandingPalette.TEXT_DIM);
    private final JLabel value = Theme.label("—", Theme.font(java.awt.Font.BOLD, 20f), LandingPalette.TEXT);
    private final JLabel change = Theme.label(" ", Theme.font(java.awt.Font.BOLD, 12f), LandingPalette.TEXT_DIM);
    private final JLabel state = Theme.label(" ", Theme.font(java.awt.Font.PLAIN, 11f), LandingPalette.TEXT_DIM);
    private final SparklineComponent spark = new SparklineComponent();
    private final String indexId;
    private final String ticker;
    private State shown = State.LOADING;
    private boolean selected;
    private boolean hover;
    private Runnable onSelect;

    public MarketIndexCard(String indexId, String indexName, String ticker) {
        this.ticker = ticker == null ? "" : ticker;
        this.indexId = indexId;
        setOpaque(false);
        setName("index." + indexId);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
        name.setText(indexName);
        name.setName("index." + indexId + ".name");
        value.setName("index." + indexId + ".value");
        change.setName("index." + indexId + ".change");
        state.setName("index." + indexId + ".state");
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(name);
        JLabel symbol = LandingPalette.label(this.ticker, Theme.font(java.awt.Font.BOLD, 11f), LandingPalette.link());
        symbol.setName("index." + indexId + ".symbol");
        text.add(symbol);
        text.add(value);
        text.add(change);
        add(text, BorderLayout.NORTH);
        add(spark, BorderLayout.CENTER);
        add(state, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(140, 140));
        getAccessibleContext().setAccessibleName(indexName + " (" + this.ticker + ")");
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { requestFocusInWindow(); fire(); }

            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }

            @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
        });
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) {
                    fire();
                }
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { repaint(); }

            @Override public void focusLost(FocusEvent e) { repaint(); }
        });
        setSnapshot(null);
    }

    public String indexId() {
        return indexId;
    }

    /** Called when the card is chosen with the mouse or keyboard. */
    public void onSelect(Runnable r) {
        this.onSelect = r;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    public boolean isSelectedCard() {
        return selected;
    }

    private void fire() {
        if (onSelect != null) {
            onSelect.run();
        }
    }

    /** Show a snapshot; {@code null} means "loading". Call on the Swing event thread. */
    public void setSnapshot(Snapshot s) {
        if (s == null || s.state() == State.LOADING) {
            shown = State.LOADING;
            value.setText("—");
            value.setFont(Theme.font(java.awt.Font.BOLD, 20f));
            value.setForeground(LandingPalette.TEXT_DIM);
            change.setText(" ");
            state.setText("Loading…");
            state.setForeground(LandingPalette.TEXT_DIM);
            spark.setValues(null, null);
            setToolTipText(null);
            describe("loading");
            return;
        }
        shown = s.state();
        if (s.state() == State.UNAVAILABLE || !s.hasValue()) {
            shown = State.UNAVAILABLE;
            value.setText("Unavailable");
            value.setFont(Theme.font(java.awt.Font.BOLD, 16f));
            value.setForeground(LandingPalette.TEXT_DIM);
            change.setText(" ");
            state.setText(shorten(s.message()));
            state.setForeground(LandingPalette.WARN);
            spark.setValues(null, null);
            setToolTipText(s.message());
            describe("unavailable: " + s.message());
            return;
        }
        value.setText(money(s.value(), s.currency()));
        value.setFont(Theme.font(java.awt.Font.BOLD, 20f));
        value.setForeground(LandingPalette.TEXT);
        Color tone = LandingPalette.TEXT_DIM;
        if (s.hasChange()) {
            tone = s.changePercent() >= 0 ? LandingPalette.UP : LandingPalette.DOWN;
            change.setText(String.format(Locale.ROOT, "%s %+.2f%%", s.changePercent() >= 0 ? "▲" : "▼", s.changePercent()));
        } else {
            change.setText("Change not reported");
        }
        change.setForeground(tone);
        String when = s.fetchedAt() == null ? "" : " · " + AT.format(s.fetchedAt());
        String who = s.provider() == null ? "" : s.provider();
        if (s.state() == State.STALE) {
            state.setText("Stale · " + who + when);
            state.setForeground(LandingPalette.WARN);
        } else {
            state.setText(who + when);
            state.setForeground(LandingPalette.TEXT_DIM);
        }
        spark.setValues(s.series(), tone);
        setToolTipText(s.message() + " · index points");
        describe(shown.name().toLowerCase(Locale.ROOT));
    }

    public State shownState() {
        return shown;
    }

    /** The price with the currency the provider stated (INR and USD get their symbol); no currency is assumed. */
    static String money(double v, String currency) {
        String n = String.format(Locale.ROOT, "%,.2f", v);
        if (currency == null || currency.isBlank()) {
            return n;
        }
        return switch (currency.toUpperCase(Locale.ROOT)) {
            case "INR" -> "\u20B9" + n;
            case "USD" -> "$" + n;
            default -> n + " " + currency.toUpperCase(Locale.ROOT);
        };
    }

    private void describe(String status) {
        getAccessibleContext().setAccessibleDescription(name.getText() + ": " + status);
    }

    /** A short form for the card's one-line state; the tooltip carries the full, actionable message. */
    static String shorten(String m) {
        if (m == null || m.isBlank()) {
            return "No data from providers";
        }
        if (m.contains(";")) {
            return "Providers failed"; // several providers said different things: the tooltip and chart caption list each one
        }
        String l = m.toLowerCase(Locale.ROOT);
        if (l.contains("api key not configured") || l.contains("not configured")) {
            return "API key missing";
        }
        if (l.contains("invalid api key")) {
            return "Invalid API key";
        }
        if (l.contains("daily quota")) {
            return "Daily quota used";
        }
        if (l.contains("rate limit")) {
            return "Rate limit reached";
        }
        if (l.contains("network")) {
            return "Network unavailable";
        }
        if (l.contains("does not support") || l.contains("not included")) {
            return "Not supported";
        }
        return m.length() > 22 ? m.substring(0, 21) + "\u2026" : m;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        LandingPalette.hints(g2);
        LandingPalette.card(g2, getWidth(), getHeight(), 18);
        if (hover) {
            g2.setColor(new Color(90, 110, 220, 28));
            g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 18, 18);
        }
        if (selected || isFocusOwner()) {
            g2.setColor(isFocusOwner() ? LandingPalette.TEXT : LandingPalette.PURPLE);
            g2.setStroke(new BasicStroke(isFocusOwner() ? 2f : 1.6f));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 18, 18);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
