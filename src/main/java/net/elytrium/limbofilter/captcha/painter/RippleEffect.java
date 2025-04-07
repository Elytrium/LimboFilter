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

/**
 * A filter to generate ripple (wave) effected images. Uses a transformed sinus
 * wave for this. This class is thread safe.
 *
 * @author akiraly
 */
public class RippleEffect implements CaptchaEffect {

  private final int[] verticalDelta;
  private final int[] horizontalDelta;

  /**
   * @param vertical   config to calculate waving deltas from x axis (so to modify y values), not null
   * @param horizontal config to calculate waving deltas from y axis (so to modify xvalues), not null
   */
  public RippleEffect(AxisConfig vertical, AxisConfig horizontal, int width, int height) {
    this.verticalDelta = this.calcDeltaArray(vertical, width);
    this.horizontalDelta = this.calcDeltaArray(horizontal, height);
  }

  /**
   * Draws a rippled (waved) variant of source into destination.
   *
   * @param src  to be transformed, not null
   * @param dest to hold the result, not null
   */
  public void filter(int width, int height, byte[] src, byte[] dest) {
    for (int x = 0; x < width; ++x) {
      for (int y = 0; y < height; ++y) {
        int ny = (y + this.verticalDelta[x] + height) % height;
        int nx = (x + this.horizontalDelta[ny] + width) % width;
        dest[ny * width + nx] = src[y * width + x];
      }
    }
  }

  /**
   * Calculates wave delta array.
   *
   * @param axisConfig config object to transform the wave, not null
   * @param num        number of points needed, positive
   * @return the calculated num length delta array
   */
  protected int[] calcDeltaArray(AxisConfig axisConfig, int num) {
    int[] delta = new int[num];

    double start = axisConfig.getStart();
    double period = axisConfig.getLength() / num;
    double amplitude = axisConfig.getAmplitude();

    for (int i = 0; i < num; ++i) {
      delta[i] = (int) Math.round(amplitude * Math.sin(start + i * period));
    }

    return delta;
  }

  @Override
  public boolean shouldCopy() {
    return true;
  }

  /**
   * Class to represent wave transforming information for an axis.
   */
  public static class AxisConfig {

    private final double start;
    private final double length;
    private final double amplitude;

    /**
     * Constructor.
     *
     * @param start     the starting x offset to generate wave values. Should be
     *                  between 0 and 2 * {@link Math#PI}.
     * @param length    the length of x to be used to generate wave values. Should
     *                  be between 0 and 4 * {@link Math#PI}.
     * @param amplitude the maximum y value, if it is too big, some important
     *                  parts of the image (like the text) can "wave" out on the
     *                  top or on the bottom of the image.
     */
    public AxisConfig(double start, double length, double amplitude) {
      this.start = this.normalize(start, 2);
      this.length = this.normalize(length, 4);
      this.amplitude = amplitude;
    }

    /**
     * Normalizes parameter to fall into [0, multi * {@link Math#PI}].
     *
     * @param a     to be normalized
     * @param multi multiplicator used for end value
     * @return normalized value
     */
    protected double normalize(double a, int multi) {
      double piMulti = multi * Math.PI;

      a = Math.abs(a);

      return a - Math.floor(a / piMulti) * piMulti;
    }

    /**
     * @return wave part start value
     */
    public double getStart() {
      return this.start;
    }

    /**
     * @return wave part length
     */
    public double getLength() {
      return this.length;
    }

    /**
     * @return amplitude used to transform the wave part
     */
    public double getAmplitude() {
      return this.amplitude;
    }
  }
}
