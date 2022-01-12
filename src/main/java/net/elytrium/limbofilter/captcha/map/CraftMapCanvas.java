/*
 * Copyright (C) 2021 Elytrium
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

package net.elytrium.limbofilter.captcha.map;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import net.elytrium.limboapi.api.protocol.map.MapPalette;
import net.elytrium.limboapi.api.protocol.packets.data.MapData;

public class CraftMapCanvas {

  private final byte[] canvas;
  private final byte[][] canvas17; // 1.7.x canvas

  public CraftMapCanvas() {
    this.canvas = new byte[MapData.MAP_SIZE];
    Arrays.fill(this.canvas, MapPalette.WHITE);

    this.canvas17 = new byte[MapData.MAP_DIM_SIZE][MapData.MAP_DIM_SIZE];
    for (int x = 0; x < MapData.MAP_DIM_SIZE; ++x) {
      for (int y = 0; y < MapData.MAP_DIM_SIZE; ++y) {
        this.canvas17[x][y] = MapPalette.WHITE;
      }
    }
  }

  public CraftMapCanvas(CraftMapCanvas another) {
    byte[] canvasBuf = new byte[MapData.MAP_SIZE];
    System.arraycopy(another.getCanvas(), 0, canvasBuf, 0, MapData.MAP_SIZE);
    this.canvas = canvasBuf;

    this.canvas17 = Arrays.stream(another.get17Canvas()).map(byte[]::clone).toArray(byte[][]::new);
  }

  public void setPixel(int x, int y, byte color) {
    if (x >= 0 && y >= 0 && x < MapData.MAP_DIM_SIZE && y < MapData.MAP_DIM_SIZE) {
      this.canvas[y * MapData.MAP_DIM_SIZE + x] = color;
      this.canvas17[x][y] = color;
    }
  }

  public void drawImage(int x, int y, BufferedImage image, boolean colorify) {
    int[] bytes = MapPalette.imageToBytes(image);
    int width = image.getWidth(null);
    int height = image.getHeight(null);
    byte randomizedColor = 0;
    if (colorify) {
      randomizedColor = (byte) ThreadLocalRandom.current().nextInt(MapPalette.getColors().length);
    }

    for (int x2 = 0; x2 < width; ++x2) {
      for (int y2 = 0; y2 < height; ++y2) {
        byte color = (byte) bytes[y2 * width + x2];
        if (color != MapPalette.WHITE) {
          if (colorify) {
            color -= randomizedColor;
            if (color < 0) {
              color += MapPalette.getColors().length;
            }
          }

          this.setPixel(x + x2, y + y2, color);
        }
      }
    }
  }

  public MapData getMapData() {
    return new MapData(this.canvas);
  }

  public MapData[] get17MapsData() {
    MapData[] maps = new MapData[MapData.MAP_DIM_SIZE];
    for (int i = 0; i < MapData.MAP_DIM_SIZE; ++i) {
      maps[i] = new MapData(i, this.canvas17[i]);
    }

    return maps;
  }

  public byte[] getCanvas() {
    return this.canvas;
  }

  public byte[][] get17Canvas() {
    return this.canvas17;
  }
}
