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

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.BitSet;
import java.util.concurrent.ThreadLocalRandom;
import net.elytrium.limbofilter.Settings;

public class RenderedFont {

  private final Int2ObjectMap<Glyph> charToGlyph = new Int2ObjectOpenHashMap<>();

  public RenderedFont(Font font, FontRenderContext ctx, char[] alphabet, int width, int height, boolean outlineEnabled,
                      float outlineMultiplier, int outlineOffsetX, int outlineOffsetY, double zoom) {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    for (char c : alphabet) {
      GlyphVector vector = font.createGlyphVector(ctx, String.valueOf(c));

      if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_ROTATE) {
        vector.setGlyphTransform(0, AffineTransform.getRotateInstance((random.nextDouble() - 0.5) * Math.PI / 8));
      }

      Point2D pos = vector.getGlyphPosition(0);
      double posX = pos.getX();
      double posY = pos.getY();
      Rectangle2D bounds = vector.getGlyphVisualBounds(0).getBounds2D();
      vector.setGlyphPosition(0, new Point2D.Double(posX - bounds.getX(), posY));

      BitSet glyphArray = new BitSet(width * height);
      Shape shape = vector.getGlyphOutline(0, -(float) width * 1.25f, -(float) height * 0.3125f);
      this.drawShape(shape, glyphArray, width, height, 0, 0, zoom);

      if (outlineEnabled) {
        Stroke stroke = new BasicStroke(outlineMultiplier);
        this.drawShape(stroke.createStrokedShape(shape), glyphArray, width, height, outlineOffsetX, outlineOffsetY, zoom);
      }

      this.charToGlyph.put(c, new Glyph(glyphArray, width, height));
    }
  }

  private void drawShape(Shape shape, BitSet array, int width, int height, int offsetX, int offsetY, double zoom) {
    Rectangle2D box = shape.getBounds2D();
    double multiplierX = box.getX() / width * zoom;
    double multiplierY = box.getY() / height * zoom;
    for (int x = 0; x < width; ++x) {
      for (int y = 0; y < height; ++y) {
        if (shape.contains(multiplierX * x, multiplierY * y)) {
          int index = (height - y - 1 + offsetY) * width + (width - x - 1 + offsetX);
          if (index >= 0 && index < array.size()) {
            array.set(index);
          }
        }
      }
    }
  }

  public Glyph getGlyph(char charToGet) {
    return this.charToGlyph.get(charToGet);
  }

  public static class Glyph {

    private final BitSet glyphData;
    private final int width;
    private final int height;

    public Glyph(BitSet glyphData, int width, int height) {
      this.glyphData = glyphData;
      this.width = width;
      this.height = height;
    }

    public BitSet getGlyphData() {
      return this.glyphData;
    }

    public int getWidth() {
      return this.width;
    }

    public int getHeight() {
      return this.height;
    }
  }
}
