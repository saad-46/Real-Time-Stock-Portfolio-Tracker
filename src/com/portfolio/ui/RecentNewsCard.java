package com.portfolio.ui;

import com.portfolio.service.NewsService;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * "Recent Market News": a column of up to five live headlines from the configured news provider, each with
 * a source badge, the source name and (when the provider gave one) how long ago it was published. It never shows built-in or
 * invented headlines: with no key, no result or an error it says why. A headline opens only when the user presses it.
 */
public class RecentNewsCard extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int MAX = 5;

    private final JPanel grid = new JPanel(new GridLayout(MAX, 1, 0, 6));
    private final JLabel note = LandingPalette.label("Loading headlines…", Theme.font(Font.PLAIN, 12f), LandingPalette.TEXT_DIM);
    private final Consumer<String> opener;
    private int shown;

    public RecentNewsCard(Consumer<String> opener) {
        this.opener = opener;
        setName("news.card");
        setOpaque(false);
        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(14, 18, 12, 18));
        add(LandingPalette.label("Recent Market News", Theme.font(Font.BOLD, 14f), LandingPalette.TEXT), BorderLayout.NORTH);
        grid.setOpaque(false);
        note.setName("news.note");
        add(new NorthPanel(grid), BorderLayout.CENTER);
        getAccessibleContext().setAccessibleName("Recent market news");
        setLoading();
    }

    public void setLoading() {
        shown = 0;
        grid.removeAll();
        grid.setLayout(new BorderLayout());
        note.setText("Loading headlines…");
        note.setForeground(LandingPalette.TEXT_DIM);
        grid.add(note, BorderLayout.NORTH);
        refresh();
    }

    public void setNews(NewsService.Live live) {
        shown = 0;
        grid.removeAll();
        java.util.List<NewsService.NewsItem> items = new java.util.ArrayList<>();
        for (NewsService.NewsItem n : live.items()) {
            if (items.size() < MAX && n.getTitle() != null && !n.getTitle().isBlank()) {
                items.add(n);
            }
        }
        if (items.isEmpty()) {
            grid.setLayout(new BorderLayout());
            note.setText(live.problem() == null ? "No headlines were returned" : live.problem());
            note.setForeground(LandingPalette.WARN);
            grid.add(note, BorderLayout.NORTH);
        } else {
            grid.setLayout(new GridLayout(MAX, 1, 0, 6));
            for (NewsService.NewsItem n : items) {
                grid.add(item(n, shown++, Instant.now()));
            }
            for (int pad = items.size(); pad < MAX; pad++) {
                grid.add(new JPanel() {
                    private static final long serialVersionUID = 1L;

                    { setOpaque(false); }
                });
            }
        }
        refresh();
    }

    public int headlineCount() {
        return shown;
    }

    private JPanel item(NewsService.NewsItem n, int index, Instant now) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        String source = n.getSource() == null || n.getSource().isBlank() ? "News" : n.getSource();
        row.add(new Badge(source), BorderLayout.WEST);
        JButton b = new JButton(n.getTitle());
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(true);
        b.setOpaque(false);
        b.setForeground(LandingPalette.TEXT);
        b.setFont(Theme.font(Font.BOLD, 12f));
        b.setHorizontalAlignment(JButton.LEFT);
        b.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setName("news.item." + index);
        b.setToolTipText(n.getTitle() + " · " + source);
        b.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { b.setForeground(LandingPalette.link()); }

            @Override public void focusLost(FocusEvent e) { b.setForeground(LandingPalette.TEXT); }
        });
        String url = n.getUrl();
        b.setEnabled(safeUrl(url));
        b.addActionListener(e -> opener.accept(url));
        String when = relative(n.getPublishedAt(), now);
        JLabel meta = new JLabel(source + (when.isEmpty() ? "" : " · " + when));
        meta.setFont(Theme.font(Font.PLAIN, 11f));
        meta.setForeground(LandingPalette.TEXT_DIM);
        meta.setName("news.meta." + index);
        JPanel text = new JPanel(new GridLayout(2, 1, 0, 0));
        text.setOpaque(false);
        text.add(b);
        text.add(meta);
        row.add(text, BorderLayout.CENTER);
        row.setPreferredSize(new Dimension(220, 44));
        return row;
    }

    /** Keeps its child at its preferred height at the top instead of stretching it. */
    private static final class NorthPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        NorthPanel(JPanel child) {
            super(new BorderLayout());
            setOpaque(false);
            add(child, BorderLayout.NORTH);
        }
    }

    /** Only http(s) links are ever opened. */
    static boolean safeUrl(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }

    /** "5 minutes ago", "2 hours ago", "3 days ago"; empty when the provider gave no usable time. */
    static String relative(String published, Instant now) {
        if (published == null || published.isBlank()) {
            return "";
        }
        Instant at;
        try {
            at = Instant.parse(published.trim());
        } catch (DateTimeParseException e) {
            try {
                at = LocalDateTime.parse(published.trim().replace(' ', 'T')).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException e2) {
                return "";
            }
        }
        Duration d = Duration.between(at, now);
        if (d.isNegative()) {
            return "";
        }
        long m = d.toMinutes();
        if (m < 1) {
            return "just now";
        }
        if (m < 60) {
            return m + (m == 1 ? " minute ago" : " minutes ago");
        }
        long h = d.toHours();
        if (h < 48) {
            return h + (h == 1 ? " hour ago" : " hours ago");
        }
        long days = d.toDays();
        return days < 60 ? days + " days ago" : DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).format(at.atOffset(ZoneOffset.UTC));
    }

    private void refresh() {
        revalidate();
        repaint();
    }

    /** A small rounded badge with the source's first letters; there are no downloaded images. */
    private static final class Badge extends JPanel {
        private static final long serialVersionUID = 1L;
        private final String letters;

        Badge(String source) {
            this.letters = source.length() >= 2 ? source.substring(0, 2).toUpperCase(Locale.ROOT) : source.toUpperCase(Locale.ROOT);
            setOpaque(false);
            setPreferredSize(new Dimension(34, 34));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            LandingPalette.hints(g2);
            g2.setPaint(new java.awt.GradientPaint(0, 0, new Color(70, 110, 230, 150), 34, 34, new Color(140, 98, 245, 150)));
            g2.fillRoundRect(0, 3, 33, 33, 10, 10);
            g2.setFont(Theme.font(Font.BOLD, 11f));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Color.WHITE);
            g2.drawString(letters, (34 - fm.stringWidth(letters)) / 2, 3 + (34 - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        LandingPalette.hints(g2);
        LandingPalette.card(g2, getWidth(), getHeight(), 18);
        g2.dispose();
        super.paintComponent(g);
    }
}
