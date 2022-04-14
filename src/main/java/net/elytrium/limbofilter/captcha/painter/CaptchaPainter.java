/*
 * Copyright (C) 2021 - 2022 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.elytrium.limbofilter.captcha.painter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import net.elytrium.limboapi.api.protocol.packets.data.MapData;
import net.elytrium.limbofilter.Settings;

public class CaptchaPainter {

  private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
  private final Random rnd = ThreadLocalRandom.current();

  public BufferedImage draw(Font font, Color foreground, String text) {
    if (font == null) {
      throw new IllegalArgumentException("Font can not be null.");
    }
    if (foreground == null) {
      throw new IllegalArgumentException("Foreground color can not be null.");
    }
    if (text == null || text.length() < 1) {
      throw new IllegalArgumentException("No text given.");
    }

    BufferedImage img = this.createImage();

    Graphics g = img.getGraphics();
    try {
      Graphics2D g2 = this.configureGraphics(g, font, foreground);
      this.draw(g2, text);
    } finally {
      g.dispose();
    }

    img = this.postProcess(img);

    g = img.getGraphics();
    try {
      Graphics2D g2 = (Graphics2D) g;
      g2.setColor(foreground);
      for (int i = 0; i < Settings.IMP.MAIN.CAPTCHA_GENERATOR.CURVES_AMOUNT; i++) {
        this.addCurve(g2);
      }
    } finally {
      g.dispose();
    }

    return img;
  }

  protected void draw(Graphics2D g, String text) {
    GlyphVector vector = g.getFont().createGlyphVector(g.getFontRenderContext(), text);

    this.transform(vector);

    Rectangle bounds = vector.getPixelBounds(null, 0, MapData.MAP_DIM_SIZE);
    float bw = (float) bounds.getWidth();
    float bh = (float) bounds.getHeight();

    boolean outlineEnabled = Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_OUTLINE;

    float wr = MapData.MAP_DIM_SIZE / bw * (this.rnd.nextFloat() / 20 + (outlineEnabled ? 0.89f : 0.92f)) * 1;
    float hr = MapData.MAP_DIM_SIZE / bh * (this.rnd.nextFloat() / 20 + (outlineEnabled ? 0.68f : 0.75f)) * 1;
    g.translate((MapData.MAP_DIM_SIZE - bw * wr) / 2, (MapData.MAP_DIM_SIZE - bh * hr) / 2);
    g.scale(wr, hr);

    float bx = (float) bounds.getX();
    float by = (float) bounds.getY();
    if (outlineEnabled) {
      g.draw(
          vector.getOutline(
              Math.signum(this.rnd.nextFloat() - 0.5f) * 1 * MapData.MAP_DIM_SIZE / 200 - bx,
              Math.signum(this.rnd.nextFloat() - 0.5f) * 1 * MapData.MAP_DIM_SIZE / 70 + MapData.MAP_DIM_SIZE - by
          )
      );
    }

    g.drawGlyphVector(vector, -bx, MapData.MAP_DIM_SIZE - by);
  }

  protected void addCurve(Graphics2D g) {
    if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.CURVE_SIZE != 0) {
      CubicCurve2D cc;

      if (this.rnd.nextBoolean()) {
        cc = new CubicCurve2D.Double(
            this.rnd.nextDouble() * MapData.MAP_DIM_SIZE, this.rnd.nextDouble() * 0.1 * MapData.MAP_DIM_SIZE,
            this.rnd.nextDouble() * MapData.MAP_DIM_SIZE, this.rnd.nextDouble() * MapData.MAP_DIM_SIZE,
            this.rnd.nextDouble() * MapData.MAP_DIM_SIZE, this.rnd.nextDouble() * MapData.MAP_DIM_SIZE,
            this.rnd.nextDouble() * MapData.MAP_DIM_SIZE, (0.8 + 0.1 * this.rnd.nextDouble()) * MapData.MAP_DIM_SIZE);
      } else {
        cc = new CubicCurve2D.Double(
            this.rnd.nextDouble() * 0.1 * MapData.MAP_DIM_SIZE, this.rnd.nextDouble() * MapData.MAP_DIM_SIZE,
            this.rnd.nextDouble() * MapData.MAP_DIM_SIZE, this.rnd.nextDouble() * MapData.MAP_DIM_SIZE,
            this.rnd.nextDouble() * MapData.MAP_DIM_SIZE, this.rnd.nextDouble() * MapData.MAP_DIM_SIZE,
            (0.8 + 0.1 * this.rnd.nextDouble()) * MapData.MAP_DIM_SIZE, this.rnd.nextDouble() * MapData.MAP_DIM_SIZE);
      }

      double[] coords = new double[6];
      PathIterator pi = cc.getPathIterator(null, 0.1);
      pi.currentSegment(coords);
      Point2D.Double prev = new Point2D.Double(coords[0], coords[1]);
      pi.next();

      g.setStroke(new BasicStroke(Settings.IMP.MAIN.CAPTCHA_GENERATOR.CURVE_SIZE));

      while (!pi.isDone()) {
        int i = pi.currentSegment(coords);
        if (i == PathIterator.SEG_MOVETO || i == PathIterator.SEG_LINETO) {
          Point2D.Double point = new Point2D.Double(coords[0], coords[1]);
          g.drawLine((int) prev.x, (int) prev.y, (int) point.x, (int) point.y);
          prev = point;
        }
        pi.next();
      }
    }
  }

  protected BufferedImage createImage() {
    return new BufferedImage(MapData.MAP_DIM_SIZE, MapData.MAP_DIM_SIZE, BufferedImage.TYPE_INT_ARGB);
  }

  protected Graphics2D configureGraphics(Graphics g, Font font, Color foreground) {
    if (!(g instanceof Graphics2D)) {
      throw new IllegalStateException("Graphics (" + g + ") that is not an instance of Graphics2D.");
    }
    Graphics2D g2 = (Graphics2D) g;

    this.configureGraphicsQuality(g2);

    g2.setColor(foreground);
    g2.setBackground(TRANSPARENT);
    g2.setFont(font);

    g2.clearRect(0, 0, MapData.MAP_DIM_SIZE, MapData.MAP_DIM_SIZE);

    return g2;
  }

  protected void configureGraphicsQuality(Graphics2D g2) {
    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    g2.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
  }

  protected void transform(GlyphVector v) {
    int glyphNum = v.getNumGlyphs();

    Point2D prePos = null;
    Rectangle2D preBounds = null;

    double rotateCur = (this.rnd.nextDouble() - 0.5) * Math.PI / 8;
    double rotateStep = Math.signum(rotateCur) * (this.rnd.nextDouble() * 3 * Math.PI / 8 / glyphNum);
    boolean rotateEnabled = Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_ROTATE;

    for (int fi = 0; fi < glyphNum; ++fi) {
      if (rotateEnabled) {
        AffineTransform tr = AffineTransform.getRotateInstance(rotateCur);
        if (this.rnd.nextDouble() < 0.25) {
          rotateStep *= -1;
        }
        rotateCur += rotateStep;
        v.setGlyphTransform(fi, tr);
      }
      Point2D pos = v.getGlyphPosition(fi);
      Rectangle2D bounds = v.getGlyphVisualBounds(fi).getBounds2D();
      Point2D newPos;
      if (prePos == null) {
        newPos = new Point2D.Double(pos.getX() - bounds.getX(), pos.getY());
      } else {
        newPos = new Point2D.Double(
            preBounds.getMaxX() + pos.getX() - bounds.getX() - Math.min(preBounds.getWidth(),
            bounds.getWidth()) * (this.rnd.nextDouble() / 20 + (rotateEnabled ? 0.27 : 0.1)),
            pos.getY()
        );
      }
      v.setGlyphPosition(fi, newPos);
      prePos = newPos;
      preBounds = v.getGlyphVisualBounds(fi).getBounds2D();
    }
  }

  protected BufferedImage postProcess(BufferedImage img) {
    if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_RIPPLE) {
      Rippler.AxisConfig vertical = new Rippler.AxisConfig(
          this.rnd.nextDouble() * 2 * Math.PI, (1 + this.rnd.nextDouble() * 2) * Math.PI, img.getHeight() / 10.0
      );
      Rippler.AxisConfig horizontal = new Rippler.AxisConfig(
          this.rnd.nextDouble() * 2 * Math.PI, (2 + this.rnd.nextDouble() * 2) * Math.PI, img.getWidth() / 100.0
      );
      Rippler op = new Rippler(vertical, horizontal);

      img = op.filter(img, this.createImage());
    }

    if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_BLUR) {
      float[] blurArray = new float[9];
      this.fillBlurArray(blurArray);
      ConvolveOp op = new ConvolveOp(new Kernel(3, 3, blurArray), ConvolveOp.EDGE_NO_OP, null);

      img = op.filter(img, this.createImage());
    }

    return img;
  }

  protected void fillBlurArray(float[] array) {
    float sum = 0;
    for (int fi = 0; fi < array.length; ++fi) {
      array[fi] = this.rnd.nextFloat();
      sum += array[fi];
    }

    for (int fi = 0; fi < array.length; ++fi) {
      array[fi] /= sum;
    }
  }
}
