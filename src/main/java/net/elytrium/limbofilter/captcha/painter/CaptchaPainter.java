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
import java.awt.Graphics2D;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.elytrium.limboapi.api.protocol.packets.data.MapData;
import net.elytrium.limboapi.api.protocol.packets.data.MapPalette;
import net.elytrium.limbofilter.Settings;

public class CaptchaPainter {

  private final ThreadLocalRandom random = ThreadLocalRandom.current();
  private final ThreadLocal<byte[][]> buffers;
  private final List<CaptchaEffect> effects = new LinkedList<>();
  private final Color curveColor = new Color(Integer.parseInt(Settings.IMP.MAIN.CAPTCHA_GENERATOR.CURVES_COLOR, 16));

  public CaptchaPainter() {
    if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_RIPPLE) {
      RippleEffect.AxisConfig vertical = new RippleEffect.AxisConfig(
          this.random.nextDouble() * 2 * Math.PI, (1 + this.random.nextDouble() * 2) * Math.PI,
          MapData.MAP_DIM_SIZE / Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_RIPPLE_AMPLITUDE_HEIGHT
      );
      RippleEffect.AxisConfig horizontal = new RippleEffect.AxisConfig(
          this.random.nextDouble() * 2 * Math.PI, (2 + this.random.nextDouble() * 2) * Math.PI,
          MapData.MAP_DIM_SIZE / Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_RIPPLE_AMPLITUDE_WIDTH
      );
      this.effects.add(new RippleEffect(vertical, horizontal, MapData.MAP_DIM_SIZE, MapData.MAP_DIM_SIZE));
    }

    this.effects.add(new OutlineEffect(Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_OUTLINE_OVERRIDE_RADIUS));

    int length = (int) this.effects.stream().filter(CaptchaEffect::shouldCopy).count();
    this.buffers = ThreadLocal.withInitial(() -> new byte[length + 1][MapData.MAP_SIZE]);
  }

  public byte[] drawCaptcha(RenderedFont font, byte foreground, String text) {
    int bufferCnt = 0;
    byte[][] buffers = this.buffers.get();
    byte[] image = buffers[bufferCnt];
    Arrays.fill(image, MapPalette.TRANSPARENT);
    this.drawText(image, font, foreground, text);

    for (CaptchaEffect e : this.effects) {
      if (e.shouldCopy()) {
        byte[] newImage = buffers[++bufferCnt];
        Arrays.fill(newImage, MapPalette.TRANSPARENT);
        e.filter(MapData.MAP_DIM_SIZE, MapData.MAP_DIM_SIZE, image, newImage);
        image = newImage;
      } else {
        e.filter(MapData.MAP_DIM_SIZE, MapData.MAP_DIM_SIZE, image, image);
      }
    }

    return image;
  }

  public int[] drawCurves() {
    BufferedImage bufferedImage = this.createImage();
    Graphics2D graphics = (Graphics2D) bufferedImage.getGraphics();
    graphics.setColor(this.curveColor);

    for (int i = 0; i < Settings.IMP.MAIN.CAPTCHA_GENERATOR.CURVES_AMOUNT; ++i) {
      this.addCurve(graphics);
    }

    return ((DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();
  }

  private void drawText(byte[] image, RenderedFont font, byte color, String text) {
    int offsetX = Settings.IMP.MAIN.CAPTCHA_GENERATOR.LETTER_OFFSET_X;
    int offsetY = Settings.IMP.MAIN.CAPTCHA_GENERATOR.LETTER_OFFSET_Y;
    int x = offsetX;
    int y = offsetY;
    int spacingX = Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_LETTER_SPACING_X;
    int spacingY = Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_LETTER_SPACING_Y;
    boolean eachWordOnSeparateLine = Settings.IMP.MAIN.CAPTCHA_GENERATOR.EACH_WORD_ON_SEPARATE_LINE;

    for (char c : text.toCharArray()) {
      RenderedFont.Glyph glyph = font.getGlyph(c);
      if (glyph == null) {
        throw new IllegalStateException("Missing glyph: " + c);
      }
      BitSet data = glyph.getGlyphData();

      int width = glyph.getWidth();
      int height = glyph.getHeight();

      for (int i = 0; i < width; ++i) {
        for (int j = 0; j < height; ++j) {
          if (data.get(j * width + i)) {
            int localX = i + x;
            int localY = j + y;
            if (localX >= 0 && localY >= y && localX < MapData.MAP_DIM_SIZE && localY < MapData.MAP_DIM_SIZE) {
              image[localY * MapData.MAP_DIM_SIZE + localX] = color;
            }
          }
        }
      }

      x += spacingX + width;
      if (x > MapData.MAP_DIM_SIZE - offsetX || (eachWordOnSeparateLine && c == ' ')) {
        x = offsetX;
        y += spacingY + (height / 2);
      }
    }
  }

  private BufferedImage createImage() {
    return new BufferedImage(MapData.MAP_DIM_SIZE, MapData.MAP_DIM_SIZE, BufferedImage.TYPE_INT_ARGB);
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
