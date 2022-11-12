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

  private final byte[][] canvas;
  private final ThreadLocal<byte[]> buffer17 = ThreadLocal.withInitial(() -> new byte[MapData.MAP_SIZE]);
  private final int width;
  private final int height;

  public CraftMapCanvas(int width, int height) {
    this.width = width;
    this.height = height;
    this.canvas = new byte[width * height][MapData.MAP_SIZE];
    Arrays.stream(this.canvas).forEach(e -> Arrays.fill(e, (byte) 0));
  }

  public CraftMapCanvas(CraftMapCanvas another) {
    this.width = another.width;
    this.height = another.height;
    this.canvas = Arrays.stream(another.getCanvas()).map(byte[]::clone).toArray(byte[][]::new);
  }

  public void drawImage(BufferedImage image, int width, int height) {
    this.drawImageCraft(MapPalette.imageToBytes(image, ProtocolVersion.MAXIMUM_VERSION), width, height);
  }

  public void drawImage(int[] image, int width, int height) {
    this.drawImageCraft(MapPalette.imageToBytes(image, ProtocolVersion.MAXIMUM_VERSION), width, height);
  }

  public void drawImageCraft(byte[] craftBytes, int width, int height) {
    for (int canvasY = 0; canvasY < this.height; canvasY++) {
      for (int canvasX = 0; canvasX < this.width; canvasX++) {
        int canvas = this.canvas.length - 1 - canvasY * this.width - canvasX;
        for (int dataY = 0; dataY < MapData.MAP_DIM_SIZE; dataY++) {
          int imageY = canvasY * MapData.MAP_DIM_SIZE + dataY;
          if (imageY >= height) {
            return;
          }

          for (int dataX = 0; dataX < MapData.MAP_DIM_SIZE; dataX++) {
            int imageX = canvasX * MapData.MAP_DIM_SIZE + dataX;
            if (imageX >= width) {
              break;
            }

            byte color = craftBytes[imageY * width + imageX];
            if (color != MapPalette.TRANSPARENT) {
              this.canvas[canvas][dataY * MapData.MAP_DIM_SIZE + dataX] = color;
            }
          }
        }
      }
    }
  }

  public void drawImageCraft(int[] craftBytes, int width, int height) {
    for (int canvasY = 0; canvasY < this.height; canvasY++) {
      for (int canvasX = 0; canvasX < this.width; canvasX++) {
        int canvas = this.canvas.length - 1 - canvasY * this.width - canvasX;
        for (int mapY = 0; mapY < MapData.MAP_DIM_SIZE; mapY++) {
          int imageY = canvasY * MapData.MAP_DIM_SIZE + mapY;
          if (imageY >= height) {
            return;
          }

          for (int mapX = 0; mapX < MapData.MAP_DIM_SIZE; mapX++) {
            int imageX = canvasX * MapData.MAP_DIM_SIZE + mapX;
            if (imageX >= width) {
              break;
            }

            byte color = (byte) craftBytes[imageY * width + imageX];
            if (color != MapPalette.TRANSPARENT) {
              this.canvas[canvas][mapY * MapData.MAP_DIM_SIZE + mapX] = color;
            }
          }
        }
      }
    }
  }

  public MapData getMapData(int index, MapPalette.MapVersion version) {
    byte[] convertedCanvas = new byte[MapData.MAP_SIZE];
    return new MapData(MapPalette.convertImage(this.canvas[index], convertedCanvas, version));
  }

  public MapData[] getMaps17Data(int index) {
    MapData[] maps = new MapData[MapData.MAP_DIM_SIZE];
    byte[] fixedCanvas = this.buffer17.get();
    Arrays.fill(fixedCanvas, (byte) 0);
    MapPalette.convertImage(this.canvas[index], fixedCanvas, MapPalette.MapVersion.MINIMUM_VERSION);

    for (int i = 0; i < MapData.MAP_DIM_SIZE; ++i) {
      byte[] canvas = new byte[MapData.MAP_DIM_SIZE];
      for (int j = 0; j < MapData.MAP_DIM_SIZE; ++j) {
        canvas[j] = fixedCanvas[j * MapData.MAP_DIM_SIZE + i];
      }

      maps[i] = new MapData(i, canvas);
    }

    return maps;
  }

  public byte[][] getCanvas() {
    return this.canvas;
  }

  public int getWidth() {
    return this.width;
  }

  public int getHeight() {
    return this.height;
  }
}
