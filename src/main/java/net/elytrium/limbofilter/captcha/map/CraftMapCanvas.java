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

package net.elytrium.limbofilter.captcha.map;

import com.velocitypowered.api.network.ProtocolVersion;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import net.elytrium.limboapi.api.protocol.packets.data.MapData;
import net.elytrium.limboapi.api.protocol.packets.data.MapPalette;

public class CraftMapCanvas {

  private final int[] canvas;

  public CraftMapCanvas() {
    this.canvas = new int[MapData.MAP_SIZE];
    Arrays.fill(this.canvas, 0);
  }

  public CraftMapCanvas(CraftMapCanvas another) {
    int[] canvasBuf = new int[MapData.MAP_SIZE];
    System.arraycopy(another.getCanvas(), 0, canvasBuf, 0, MapData.MAP_SIZE);
    this.canvas = canvasBuf;
  }

  public void setPixel(int x, int y, int color) {
    if (x >= 0 && y >= 0 && x < MapData.MAP_DIM_SIZE && y < MapData.MAP_DIM_SIZE) {
      this.canvas[y * MapData.MAP_DIM_SIZE + x] = color;
    }
  }

  public void drawImage(int x, int y, BufferedImage image) {
    this.drawImageCraft(x, y, MapPalette.imageToBytes(image, ProtocolVersion.MAXIMUM_VERSION));
  }

  public void drawImage(int x, int y, int[] image) {
    this.drawImageCraft(x, y, MapPalette.imageToBytes(image, ProtocolVersion.MAXIMUM_VERSION));
  }

  public void drawImageCraft(int x, int y, int[] craftBytes) {
    int width = MapData.MAP_DIM_SIZE;
    int height = MapData.MAP_DIM_SIZE;

    for (int x2 = 0; x2 < width; ++x2) {
      for (int y2 = 0; y2 < height; ++y2) {
        int color = craftBytes[y2 * width + x2];
        if (color != MapPalette.TRANSPARENT) {
          this.setPixel(x + x2, y + y2, color);
        }
      }
    }
  }

  public MapData getMapData(MapPalette.MapVersion version) {
    int[] fixedCanvas = MapPalette.convertImage(this.canvas, version);
    byte[] canvas = new byte[MapData.MAP_SIZE];
    for (int i = 0; i < MapData.MAP_SIZE; ++i) {
      canvas[i] = (byte) fixedCanvas[i];
    }

    return new MapData(canvas);
  }

  public MapData[] getMaps17Data() {
    MapData[] maps = new MapData[MapData.MAP_DIM_SIZE];
    int[] fixedCanvas = MapPalette.convertImage(this.canvas, MapPalette.MapVersion.MINIMUM_VERSION);

    for (int i = 0; i < MapData.MAP_DIM_SIZE; ++i) {
      byte[] canvas = new byte[MapData.MAP_DIM_SIZE];
      for (int j = 0; j < MapData.MAP_DIM_SIZE; ++j) {
        canvas[j] = (byte) fixedCanvas[j * MapData.MAP_DIM_SIZE + i];
      }

      maps[i] = new MapData(i, canvas);
    }

    return maps;
  }

  public int[] getCanvas() {
    return this.canvas;
  }
}
