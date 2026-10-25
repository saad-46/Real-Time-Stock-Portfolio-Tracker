package com.portfolio.ui;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;

/**
 * Removes characters the component's font cannot draw. Java's Swing text does not fall back to an emoji font, so an emoji in a
 * label or button shows as an empty square. Components that deliberately use an emoji-capable font keep their symbols.
 */
final class Glyphs {
    private static boolean listening;

    private Glyphs() {
    }

    /** Cleans every window (dialogs, option panes) as it opens. Safe to call more than once. */
    static synchronized void installForOpeningWindows() {
        if (listening) {
            return;
        }
        listening = true;
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof java.awt.event.WindowEvent we && we.getID() == java.awt.event.WindowEvent.WINDOW_OPENED) {
                java.awt.Window w = we.getWindow();
                javax.swing.SwingUtilities.invokeLater(() -> sweep(w));
            }
        }, java.awt.AWTEvent.WINDOW_EVENT_MASK);
    }

    /** Text without the code points {@code font} cannot display (and without variation selectors / joiners). */
    static String clean(String text, Font font) {
        return font == null ? text : clean(text, font::canDisplay);
    }

    static String clean(String text, java.util.function.IntPredicate canDisplay) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        boolean changed = false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            boolean invisible = cp == 0xFE0F || cp == 0xFE0E || cp == 0x200D;
            if (invisible || (cp > 0x7E && !Character.isWhitespace(cp) && !canDisplay.test(cp))) {
                changed = true;
                continue;
            }
            out.appendCodePoint(cp);
        }
        if (!changed) {
            return text;
        }
        String result = out.toString().strip();
        return result.replaceAll("\s{2,}", " ");
    }

    /** Cleans every label and button below {@code root}. Safe to call repeatedly. */
    static void sweep(Component root) {
        if (root instanceof JLabel label) {
            String cleaned = clean(label.getText(), label.getFont());
            if (cleaned != null && !cleaned.equals(label.getText())) {
                label.setText(cleaned);
            }
        } else if (root instanceof AbstractButton button) {
            String cleaned = clean(button.getText(), button.getFont());
            if (cleaned != null && !cleaned.equals(button.getText())) {
                button.setText(cleaned);
            }
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                sweep(child);
            }
        }
    }
}
