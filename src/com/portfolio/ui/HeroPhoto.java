package com.portfolio.ui;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * The hero's background: a photograph of the BSE building (bundled resource {@code /images/bse-building.jpg}), cropped to cover the
 * hero and veiled with a left-to-right and bottom gradient in the page colour so that the text, icons and buttons on top stay
 * readable in both themes. If the picture cannot be loaded the hero simply has the plain page background.
 */
final class HeroPhoto {
    private static BufferedImage photo;
    private static boolean tried;

    private HeroPhoto() {
    }

    static synchronized BufferedImage photo() {
        if (!tried) {
            tried = true;
            try (InputStream in = HeroPhoto.class.getResourceAsStream("/images/bse-building.jpg")) {
                if (in != null) {
                    photo = ImageIO.read(in);
                }
            } catch (IOException | RuntimeException e) {
                photo = null;
            }
        }
        return photo;
    }

    /** Paints into the area (0,0,w,h). */
    static void paint(Graphics2D g2, int w, int h) {
        BufferedImage img = photo();
        if (img == null || w < 40 || h < 40) {
            return;
        }
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        double scale = Math.max(w / (double) img.getWidth(), h / (double) img.getHeight());
        int dw = (int) Math.ceil(img.getWidth() * scale);
        int dh = (int) Math.ceil(img.getHeight() * scale);
        // keep the tall curved tower in view: align the crop towards the left-centre of the picture
        int dx = -(int) ((dw - w) * 0.30);
        int dy = -(dh - h) / 2;
        java.awt.Composite old = g2.getComposite();
        g2.setComposite(java.awt.AlphaComposite.SrcOver.derive(LandingPalette.dark ? 0.55f : 0.50f));
        g2.drawImage(img, dx, dy, dw, dh, null);
        g2.setComposite(old);

        // the page behind the hero is a vertical gradient: use its middle colour so the veil's edges match the page
        Color top = LandingPalette.NAVY_TOP;
        Color bottom = LandingPalette.NAVY_BOTTOM;
        Color base = new Color((top.getRed() + bottom.getRed()) / 2, (top.getGreen() + bottom.getGreen()) / 2, (top.getBlue() + bottom.getBlue()) / 2);
        int r = base.getRed();
        int g = base.getGreen();
        int b = base.getBlue();
        // veil: strongest behind the text on the left, lighter on the right so the building still shows
        g2.setPaint(new GradientPaint(0, 0, new Color(r, g, b, 238), (float) (w * 0.95), 0, new Color(r, g, b, 120)));
        g2.fillRect(0, 0, w, h);
        // the right edge melts into the page so the picture has no hard border next to the market cards
        g2.setPaint(new GradientPaint((float) (w * 0.80), 0, new Color(r, g, b, 0), w, 0, new Color(r, g, b, 255)));
        g2.fillRect((int) (w * 0.80), 0, w - (int) (w * 0.80), h);
        // soften the top and bottom edges into the page
        g2.setPaint(new GradientPaint(0, 0, new Color(r, g, b, 200), 0, (float) (h * 0.14), new Color(r, g, b, 0)));
        g2.fillRect(0, 0, w, (int) (h * 0.14) + 1);
        g2.setPaint(new GradientPaint(0, (float) (h * 0.78), new Color(r, g, b, 0), 0, h, new Color(r, g, b, 235)));
        g2.fillRect(0, (int) (h * 0.78), w, h - (int) (h * 0.78));
    }
}
