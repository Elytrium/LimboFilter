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

public class OutlineOverrideEffect implements CaptchaEffect {
  private final int blurRadius;

  public OutlineOverrideEffect(int blurRadius) {
    this.blurRadius = blurRadius;
  }

  public void filter(int width, int height, int[] src, int[] dest) {
    for (int x = this.blurRadius; x < height - this.blurRadius; x++) {
      for (int y = this.blurRadius; y < width - this.blurRadius; y++) {
        if (src[y * width + x] != 0) {
          boolean found = false;
          for (int blurX = x - this.blurRadius; blurX <= x + this.blurRadius; blurX++) {
            for (int blurY = y - this.blurRadius; blurY <= y + this.blurRadius; blurY++) {
              if (src[blurY * width + blurX] == 0) {
                found = true;
                break;
              }
            }
            if (found) {
              break;
            }
          }

          if (found) {
            dest[y * width + x] = src[y * width + x] & 0x80808080;
          } else {
            dest[y * width + x] = src[y * width + x];
          }
        }
      }
    }
  }
}
