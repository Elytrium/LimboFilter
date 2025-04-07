/*
 * Copyright (C) 2021 - 2025 Elytrium
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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import net.elytrium.limboapi.api.protocol.packets.data.MapPalette;
import net.elytrium.limbofilter.Settings;

public class CaptchaPainter {

  private final ThreadLocalRandom random = ThreadLocalRandom.current();
  private final ThreadLocal<byte[][]> buffers;
  private final List<CaptchaEffect> effects = new LinkedList<>();
  private final List<Color> curveColor;
  private final int width;
  private final int height;
  private ThreadLocal<Iterator<Color>> curveColorIterator;

  public CaptchaPainter(int width, int height) {
    if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_RIPPLE) {
      RippleEffect.AxisConfig vertical = new RippleEffect.AxisConfig(
          this.random.nextDouble() * 2 * Math.PI, (1 + this.random.nextDouble() * 2) * Math.PI,
          height / Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_RIPPLE_AMPLITUDE_HEIGHT
      );
      RippleEffect.AxisConfig horizontal = new RippleEffect.AxisConfig(
          this.random.nextDouble() * 2 * Math.PI, (2 + this.random.nextDouble() * 2) * Math.PI,
          width / Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_RIPPLE_AMPLITUDE_WIDTH
      );
      this.effects.add(new RippleEffect(vertical, horizontal, width, height));
    }

    this.effects.add(new OutlineEffect(Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_OUTLINE_OVERRIDE_RADIUS));

    int length = (int) this.effects.stream().filter(CaptchaEffect::shouldCopy).count();
    this.buffers = ThreadLocal.withInitial(() -> new byte[length + 1][width * height]);
    this.width = width;
    this.height = height;

    if (!Settings.IMP.MAIN.CAPTCHA_GENERATOR.CURVES_COLORS.isEmpty()) {
      this.curveColor = Settings.IMP.MAIN.CAPTCHA_GENERATOR.CURVES_COLORS.stream()
          .map(c -> new Color(Integer.parseInt(c, 16)))
          .collect(Collectors.toUnmodifiableList());
      this.curveColorIterator = ThreadLocal.withInitial(this.curveColor::iterator);
    } else {
      this.curveColor = null;
    }
  }

  public byte[] drawCaptcha(RenderedFont font, byte[] foreground, String text) {
    int bufferCnt = 0;
    byte[][] buffers = this.buffers.get();
    byte[] image = buffers[bufferCnt];
    Arrays.fill(image, MapPalette.TRANSPARENT);
    this.drawText(image, font, foreground, text);

    for (CaptchaEffect e : this.effects) {
      if (e.shouldCopy()) {
        byte[] newImage = buffers[++bufferCnt];
        Arrays.fill(newImage, MapPalette.TRANSPARENT);
        e.filter(this.width, this.height, image, newImage);
        image = newImage;
      } else {
        e.filter(this.width, this.height, image, image);
      }
    }

    return image;
  }

  public int[] drawCurves() {
    if (this.curveColor == null || Settings.IMP.MAIN.CAPTCHA_GENERATOR.CURVES_AMOUNT == 0) {
      return null;
    }

    BufferedImage bufferedImage = this.createImage();
    Graphics2D graphics = (Graphics2D) bufferedImage.getGraphics();
    if (!this.curveColorIterator.get().hasNext()) {
      this.curveColorIterator.set(this.curveColor.iterator());
    }

    graphics.setColor(this.curveColorIterator.get().next());

    for (int i = 0; i < Settings.IMP.MAIN.CAPTCHA_GENERATOR.CURVES_AMOUNT; ++i) {
      this.addCurve(graphics);
    }

    return ((DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();
  }

  private void drawText(byte[] image, RenderedFont font, byte[] colors, String text) {
    boolean scaleFont = Settings.IMP.MAIN.FRAMED_CAPTCHA.FRAMED_CAPTCHA_ENABLED && Settings.IMP.MAIN.FRAMED_CAPTCHA.AUTOSCALE_FONT;
    int multiplierX = scaleFont ? Settings.IMP.MAIN.FRAMED_CAPTCHA.WIDTH : 1;
    int multiplierY = scaleFont ? Settings.IMP.MAIN.FRAMED_CAPTCHA.HEIGHT : 1;

    int offsetX = Settings.IMP.MAIN.CAPTCHA_GENERATOR.LETTER_OFFSET_X * multiplierX;
    int offsetY = Settings.IMP.MAIN.CAPTCHA_GENERATOR.LETTER_OFFSET_Y * multiplierY;
    int x = offsetX;
    int y = offsetY;
    int spacingX = Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_LETTER_SPACING_X * multiplierX;
    int spacingY = Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_LETTER_SPACING_Y * multiplierY;
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
            if (localX >= 0 && localY >= y && localX < this.width && localY < this.height) {
              image[localY * this.width + localX] = colors[(localY * this.width + localX) % colors.length];
            }
          }
        }
      }

      x += spacingX + width;
      if (x > this.width - offsetX || (eachWordOnSeparateLine && c == ' ')) {
        x = offsetX;
        y += spacingY + (height / 2);
      }
    }
  }

  private BufferedImage createImage() {
    return new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB);
  }

  private void addCurve(Graphics2D graphics) {
    if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.CURVE_SIZE != 0) {
      CubicCurve2D cubicCurve;

      if (this.random.nextBoolean()) {
        cubicCurve = new CubicCurve2D.Double(
            this.random.nextDouble() * this.width, this.random.nextDouble() * 0.1 * this.height,
            this.random.nextDouble() * this.width, this.random.nextDouble() * this.height,
            this.random.nextDouble() * this.width, this.random.nextDouble() * this.height,
            this.random.nextDouble() * this.width, (0.8 + 0.1 * this.random.nextDouble()) * this.height
        );
      } else {
        cubicCurve = new CubicCurve2D.Double(
            this.random.nextDouble() * 0.1 * this.width, this.random.nextDouble() * this.height,
            this.random.nextDouble() * this.width, this.random.nextDouble() * this.height,
            this.random.nextDouble() * this.width, this.random.nextDouble() * this.height,
            (0.8 + 0.1 * this.random.nextDouble()) * this.width, this.random.nextDouble() * this.height
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

  public int getWidth() {
    return this.width;
  }

  public int getHeight() {
    return this.height;
  }
}
