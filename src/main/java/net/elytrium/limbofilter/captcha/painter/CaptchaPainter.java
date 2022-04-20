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
import java.util.concurrent.ThreadLocalRandom;
import net.elytrium.limboapi.api.protocol.packets.data.MapData;
import net.elytrium.limbofilter.Settings;

public class CaptchaPainter {

  private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

  private final ThreadLocalRandom random = ThreadLocalRandom.current();

  public BufferedImage drawCaptcha(Font font, Color foreground, String text) {
    BufferedImage image = this.createImage();
    Graphics2D graphics = (Graphics2D) image.getGraphics();

    this.drawText(this.configureGraphics(graphics, font, foreground), text);

    graphics.dispose();

    image = this.postProcess(image);
    graphics = (Graphics2D) image.getGraphics();

    graphics.setColor(foreground);
    for (int i = 0; i < Settings.IMP.MAIN.CAPTCHA_GENERATOR.CURVES_AMOUNT; ++i) {
      this.addCurve(graphics);
    }

    graphics.dispose();

    return image;
  }

  private Graphics2D configureGraphics(Graphics2D graphics, Font font, Color foreground) {
    this.configureGraphicsQuality(graphics);

    graphics.setColor(foreground);
    graphics.setBackground(TRANSPARENT);
    graphics.setFont(font);

    graphics.clearRect(0, 0, MapData.MAP_DIM_SIZE, MapData.MAP_DIM_SIZE);

    return graphics;
  }

  private void configureGraphicsQuality(Graphics2D graphics) {
    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    graphics.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
  }

  private void drawText(Graphics2D graphics, String text) {
    GlyphVector vector = graphics.getFont().createGlyphVector(graphics.getFontRenderContext(), text);

    this.transform(vector);

    Rectangle bounds = vector.getPixelBounds(null, 0, MapData.MAP_DIM_SIZE);
    float boundsWidth = (float) bounds.getWidth();
    float boundsHeight = (float) bounds.getHeight();

    boolean outlineEnabled = Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_OUTLINE;

    float wr = MapData.MAP_DIM_SIZE / boundsWidth * (this.random.nextFloat() / 20 + (outlineEnabled ? 0.89f : 0.92f)) * 1;
    float hr = MapData.MAP_DIM_SIZE / boundsHeight * (this.random.nextFloat() / 20 + (outlineEnabled ? 0.68f : 0.75f)) * 1;
    graphics.translate((MapData.MAP_DIM_SIZE - boundsWidth * wr) / 2, (MapData.MAP_DIM_SIZE - boundsHeight * hr) / 2);
    graphics.scale(wr, hr);

    float boundsX = (float) bounds.getX();
    float boundsY = (float) bounds.getY();
    if (outlineEnabled) {
      graphics.draw(
          vector.getOutline(
              Math.signum(this.random.nextFloat() - 0.5f) * 1 * MapData.MAP_DIM_SIZE / 200 - boundsX,
              Math.signum(this.random.nextFloat() - 0.5f) * 1 * MapData.MAP_DIM_SIZE / 70 + MapData.MAP_DIM_SIZE - boundsY
          )
      );
    }

    graphics.drawGlyphVector(vector, -boundsX, MapData.MAP_DIM_SIZE - boundsY);
  }

  private void transform(GlyphVector vector) {
    int glyphNum = vector.getNumGlyphs();

    Point2D prePos = null;
    Rectangle2D preBounds = null;

    double rotateCur = (this.random.nextDouble() - 0.5) * Math.PI / 8;
    double rotateStep = Math.signum(rotateCur) * (this.random.nextDouble() * 3 * Math.PI / 8 / glyphNum);
    boolean rotateEnabled = Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_ROTATE;

    for (int i = 0; i < glyphNum; ++i) {
      if (rotateEnabled) {
        AffineTransform transform = AffineTransform.getRotateInstance(rotateCur);
        if (this.random.nextDouble() < 0.25) {
          rotateStep *= -1;
        }

        rotateCur += rotateStep;
        vector.setGlyphTransform(i, transform);
      }

      Point2D pos = vector.getGlyphPosition(i);
      double posX = pos.getX();
      double posY = pos.getY();
      Rectangle2D bounds = vector.getGlyphVisualBounds(i).getBounds2D();
      double boundsX = bounds.getX();

      Point2D newPos;
      if (prePos == null) {
        newPos = new Point2D.Double(posX - boundsX, posY);
      } else {
        newPos = new Point2D.Double(
            preBounds.getMaxX() + posX - boundsX - Math.min(preBounds.getWidth(), bounds.getWidth())
                * (this.random.nextDouble() / 20 + (rotateEnabled ? 0.27 : 0.1)),
            posY
        );
      }
      vector.setGlyphPosition(i, newPos);
      prePos = newPos;
      preBounds = vector.getGlyphVisualBounds(i).getBounds2D();
    }
  }

  private BufferedImage postProcess(BufferedImage image) {
    if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_RIPPLE) {
      Rippler.AxisConfig vertical = new Rippler.AxisConfig(
          this.random.nextDouble() * 2 * Math.PI, (1 + this.random.nextDouble() * 2) * Math.PI, image.getHeight() / 10.0
      );
      Rippler.AxisConfig horizontal = new Rippler.AxisConfig(
          this.random.nextDouble() * 2 * Math.PI, (2 + this.random.nextDouble() * 2) * Math.PI, image.getWidth() / 100.0
      );

      image = new Rippler(vertical, horizontal).filter(image, this.createImage());
    }

    if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_BLUR) {
      float[] blurArray = new float[9];
      this.fillBlurArray(blurArray);

      image = new ConvolveOp(new Kernel(3, 3, blurArray), ConvolveOp.EDGE_NO_OP, null).filter(image, this.createImage());
    }

    return image;
  }

  private BufferedImage createImage() {
    return new BufferedImage(MapData.MAP_DIM_SIZE, MapData.MAP_DIM_SIZE, BufferedImage.TYPE_INT_ARGB);
  }

  private void fillBlurArray(float[] blurArray) {
    float sum = 0;
    for (int i = 0; i < blurArray.length; ++i) {
      blurArray[i] = this.random.nextFloat();
      sum += blurArray[i];
    }

    for (int i = 0; i < blurArray.length; ++i) {
      blurArray[i] /= sum;
    }
  }

  private void addCurve(Graphics2D graphics) {
    if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.CURVE_SIZE != 0) {
      CubicCurve2D cubicCurve;

      if (this.random.nextBoolean()) {
        cubicCurve = new CubicCurve2D.Double(
            this.random.nextDouble() * MapData.MAP_DIM_SIZE, this.random.nextDouble() * 0.1 * MapData.MAP_DIM_SIZE,
            this.random.nextDouble() * MapData.MAP_DIM_SIZE, this.random.nextDouble() * MapData.MAP_DIM_SIZE,
            this.random.nextDouble() * MapData.MAP_DIM_SIZE, this.random.nextDouble() * MapData.MAP_DIM_SIZE,
            this.random.nextDouble() * MapData.MAP_DIM_SIZE, (0.8 + 0.1 * this.random.nextDouble()) * MapData.MAP_DIM_SIZE
        );
      } else {
        cubicCurve = new CubicCurve2D.Double(
            this.random.nextDouble() * 0.1 * MapData.MAP_DIM_SIZE, this.random.nextDouble() * MapData.MAP_DIM_SIZE,
            this.random.nextDouble() * MapData.MAP_DIM_SIZE, this.random.nextDouble() * MapData.MAP_DIM_SIZE,
            this.random.nextDouble() * MapData.MAP_DIM_SIZE, this.random.nextDouble() * MapData.MAP_DIM_SIZE,
            (0.8 + 0.1 * this.random.nextDouble()) * MapData.MAP_DIM_SIZE, this.random.nextDouble() * MapData.MAP_DIM_SIZE
        );
      }

      double[] coords = new double[6];
      PathIterator pathIterator = cubicCurve.getPathIterator(null, 0.1);
      pathIterator.currentSegment(coords);
      Point2D.Double prev = new Point2D.Double(coords[0], coords[1]);
      pathIterator.next();

      graphics.setStroke(new BasicStroke(Settings.IMP.MAIN.CAPTCHA_GENERATOR.CURVE_SIZE));

      while (!pathIterator.isDone()) {
        int currentSegment = pathIterator.currentSegment(coords);
        if (currentSegment == PathIterator.SEG_MOVETO || currentSegment == PathIterator.SEG_LINETO) {
          Point2D.Double point = new Point2D.Double(coords[0], coords[1]);
          graphics.drawLine((int) prev.x, (int) prev.y, (int) point.x, (int) point.y);
          prev = point;
        }

        pathIterator.next();
      }
    }
  }
}
