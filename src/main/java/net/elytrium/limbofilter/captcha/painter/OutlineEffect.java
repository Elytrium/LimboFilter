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

public class OutlineEffect implements CaptchaEffect {
  private final int borderRadius;

  public OutlineEffect(int borderRadius) {
    this.borderRadius = borderRadius;
  }

  public void filter(int width, int height, byte[] src, byte[] dest) {
    for (int x = this.borderRadius; x < width - this.borderRadius; x++) {
      for (int y = this.borderRadius; y < height - this.borderRadius; y++) {
        if (src[y * width + x] != 0) {
          boolean found = false;
          for (int blurX = x - this.borderRadius; blurX <= x + this.borderRadius; blurX++) {
            for (int blurY = y - this.borderRadius; blurY <= y + this.borderRadius; blurY++) {
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
            dest[y * width + x] = (byte) (src[y * width + x] & ~0b11);
          } else {
            dest[y * width + x] = src[y * width + x];
          }
        }
      }
    }
  }

  @Override
  public boolean shouldCopy() {
    return false;
  }
}
