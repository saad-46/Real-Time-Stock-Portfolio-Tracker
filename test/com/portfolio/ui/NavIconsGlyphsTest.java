package com.portfolio.ui;

import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NavIconsGlyphsTest {
    private static final List<String> PAGES = List.of("Dashboard", "My Portfolio", "My Wallet", "Market", "Watchlist",
            "Transactions", "Analytics", "Market News", "Settings");

    @Test
    void everyPageHasItsOwnIconOfTheSameSizeThatActuallyDraws() {
        Set<NavIcons.Kind> kinds = EnumSet.noneOf(NavIcons.Kind.class);
        for (String page : PAGES) {
            NavIcons.Kind kind = NavIcons.forPage(page);
            assertTrue(kinds.add(kind), page + " shares an icon with another page");
            Icon icon = NavIcons.of(kind, () -> Color.WHITE);
            assertEquals(NavIcons.SIZE, icon.getIconWidth());
            assertEquals(NavIcons.SIZE, icon.getIconHeight());
            BufferedImage img = new BufferedImage(NavIcons.SIZE, NavIcons.SIZE, BufferedImage.TYPE_INT_ARGB);
            icon.paintIcon(null, img.getGraphics(), 0, 0);
            int painted = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    if ((img.getRGB(x, y) >>> 24) != 0) painted++;
                }
            }
            assertTrue(painted > 20, page + " icon is blank (" + painted + " pixels)");
        }
    }

    @Test
    void iconColourFollowsTheThemeWithoutRebuilding() {
        Color[] current = { Color.RED };
        Icon icon = NavIcons.of(NavIcons.Kind.SETTINGS, () -> current[0]);
        BufferedImage a = new BufferedImage(22, 22, BufferedImage.TYPE_INT_ARGB);
        icon.paintIcon(null, a.getGraphics(), 0, 0);
        current[0] = Color.BLUE;
        BufferedImage b = new BufferedImage(22, 22, BufferedImage.TYPE_INT_ARGB);
        icon.paintIcon(null, b.getGraphics(), 0, 0);
        boolean differs = false;
        for (int y = 0; y < 22 && !differs; y++) {
            for (int x = 0; x < 22; x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    differs = true;
                    break;
                }
            }
        }
        assertTrue(differs);
    }

    @Test
    void symbolsTheFontCannotDrawAreRemovedAndRealTextIsKept() {
        java.util.function.IntPredicate basic = cp -> cp <= 0x20B9; // Latin text and the rupee sign
        assertEquals("My Wallet", Glyphs.clean("🧱 My Wallet", basic));
        assertEquals("Top Gainers", Glyphs.clean("🚀  Top Gainers", basic));
        assertEquals("Settings", Glyphs.clean("⚙️ Settings", basic));
        assertEquals("Total ₹1,000.00", Glyphs.clean("Total ₹1,000.00", basic));
        assertEquals("Plain text", Glyphs.clean("Plain text", basic));
        assertNull(Glyphs.clean(null, basic));
    }
}
