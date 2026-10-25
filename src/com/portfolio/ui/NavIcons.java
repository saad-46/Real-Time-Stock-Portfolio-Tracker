package com.portfolio.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Navigation icons drawn with Java2D: the same size and stroke everywhere, coloured by the caller, and independent of any
 * emoji font (which is what produced the missing-glyph squares). Each icon is a 24x24 line drawing scaled to {@link #SIZE}.
 */
final class NavIcons {
    static final int SIZE = 22;

    enum Kind { DASHBOARD, PORTFOLIO, WALLET, MARKET, WATCHLIST, TRANSACTIONS, ANALYTICS, NEWS, SETTINGS, ASSISTANT }

    private NavIcons() {
    }

    static Kind forPage(String page) {
        return switch (page) {
            case "Dashboard" -> Kind.DASHBOARD;
            case "My Portfolio" -> Kind.PORTFOLIO;
            case "My Wallet" -> Kind.WALLET;
            case "Market" -> Kind.MARKET;
            case "Watchlist" -> Kind.WATCHLIST;
            case "Transactions" -> Kind.TRANSACTIONS;
            case "Analytics" -> Kind.ANALYTICS;
            case "Market News" -> Kind.NEWS;
            case "Settings" -> Kind.SETTINGS;
            default -> Kind.DASHBOARD;
        };
    }

    /** An icon whose colour is read every time it paints, so theme and active-state changes need no rebuild. */
    static Icon of(Kind kind, java.util.function.Supplier<Color> color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                    g2.translate(x, y);
                    double s = SIZE / 24.0;
                    g2.scale(s, s);
                    g2.setColor(color.get());
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    draw(g2, kind);
                } finally {
                    g2.dispose();
                }
            }

            @Override
            public int getIconWidth() {
                return SIZE;
            }

            @Override
            public int getIconHeight() {
                return SIZE;
            }
        };
    }

    private static void draw(Graphics2D g, Kind kind) {
        switch (kind) {
            case DASHBOARD -> { // four tiles
                g.draw(new RoundRectangle2D.Double(3, 3, 7, 7, 2, 2));
                g.draw(new RoundRectangle2D.Double(14, 3, 7, 7, 2, 2));
                g.draw(new RoundRectangle2D.Double(3, 14, 7, 7, 2, 2));
                g.draw(new RoundRectangle2D.Double(14, 14, 7, 7, 2, 2));
            }
            case PORTFOLIO -> { // briefcase
                g.draw(new RoundRectangle2D.Double(3, 7, 18, 13, 3, 3));
                Path2D handle = new Path2D.Double();
                handle.moveTo(9, 7);
                handle.lineTo(9, 5);
                handle.quadTo(9, 4, 10, 4);
                handle.lineTo(14, 4);
                handle.quadTo(15, 4, 15, 5);
                handle.lineTo(15, 7);
                g.draw(handle);
                g.drawLine(3, 13, 21, 13);
            }
            case WALLET -> {
                g.draw(new RoundRectangle2D.Double(3, 6, 18, 14, 3, 3));
                Path2D flap = new Path2D.Double();
                flap.moveTo(5, 6);
                flap.lineTo(16, 3.5);
                flap.lineTo(16, 6);
                g.draw(flap);
                g.fill(new Ellipse2D.Double(15.2, 12, 2.6, 2.6));
            }
            case MARKET -> { // rising line on axes
                Path2D axes = new Path2D.Double();
                axes.moveTo(3, 3);
                axes.lineTo(3, 21);
                axes.lineTo(21, 21);
                g.draw(axes);
                Path2D line = new Path2D.Double();
                line.moveTo(7, 16);
                line.lineTo(11, 11);
                line.lineTo(14, 14);
                line.lineTo(20, 7);
                g.draw(line);
            }
            case WATCHLIST -> { // star
                Path2D star = new Path2D.Double();
                for (int i = 0; i < 10; i++) {
                    double r = i % 2 == 0 ? 9.5 : 4.2;
                    double a = -Math.PI / 2 + i * Math.PI / 5;
                    double px = 12 + r * Math.cos(a);
                    double py = 12.6 + r * Math.sin(a);
                    if (i == 0) star.moveTo(px, py);
                    else star.lineTo(px, py);
                }
                star.closePath();
                g.draw(star);
            }
            case TRANSACTIONS -> { // two opposing arrows
                g.drawLine(4, 8, 19, 8);
                g.drawLine(15, 4, 19, 8);
                g.drawLine(15, 12, 19, 8);
                g.drawLine(20, 16, 5, 16);
                g.drawLine(9, 12, 5, 16);
                g.drawLine(9, 20, 5, 16);
            }
            case ANALYTICS -> { // bars
                g.draw(new RoundRectangle2D.Double(4, 12, 4, 8, 1, 1));
                g.draw(new RoundRectangle2D.Double(10, 4, 4, 16, 1, 1));
                g.draw(new RoundRectangle2D.Double(16, 9, 4, 11, 1, 1));
            }
            case NEWS -> { // newspaper
                g.draw(new RoundRectangle2D.Double(3, 4, 18, 16, 3, 3));
                g.draw(new RoundRectangle2D.Double(6.5, 8, 5, 4.5, 1, 1));
                g.drawLine(14, 8, 18, 8);
                g.drawLine(14, 12, 18, 12);
                g.drawLine(7, 16, 18, 16);
            }
            case SETTINGS -> { // gear: ring with eight teeth
                g.draw(new Ellipse2D.Double(8.5, 8.5, 7, 7));
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    g.drawLine((int) Math.round(12 + 7.2 * Math.cos(a)), (int) Math.round(12 + 7.2 * Math.sin(a)),
                            (int) Math.round(12 + 9.8 * Math.cos(a)), (int) Math.round(12 + 9.8 * Math.sin(a)));
                }
                g.draw(new Ellipse2D.Double(4.8, 4.8, 14.4, 14.4));
            }
            case ASSISTANT -> { // chat bubble with dots
                Path2D bubble = new Path2D.Double();
                bubble.moveTo(5, 4);
                bubble.lineTo(19, 4);
                bubble.quadTo(21, 4, 21, 6);
                bubble.lineTo(21, 14);
                bubble.quadTo(21, 16, 19, 16);
                bubble.lineTo(11, 16);
                bubble.lineTo(6, 20.5);
                bubble.lineTo(6, 16);
                bubble.lineTo(5, 16);
                bubble.quadTo(3, 16, 3, 14);
                bubble.lineTo(3, 6);
                bubble.quadTo(3, 4, 5, 4);
                g.draw(bubble);
                for (int i = 0; i < 3; i++) {
                    g.fill(new Ellipse2D.Double(7.4 + i * 3.8, 8.9, 2.2, 2.2));
                }
            }
            default -> g.draw(new Arc2D.Double(4, 4, 16, 16, 0, 360, Arc2D.OPEN));
        }
    }
}
