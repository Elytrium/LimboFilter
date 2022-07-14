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

  private final byte[] canvas;
  private final ThreadLocal<byte[]> buffer17 = ThreadLocal.withInitial(() -> new byte[MapData.MAP_SIZE]);

  public CraftMapCanvas() {
    this.canvas = new byte[MapData.MAP_SIZE];
    Arrays.fill(this.canvas, (byte) 0);
  }

  public CraftMapCanvas(CraftMapCanvas another) {
    this.canvas = Arrays.copyOf(another.getCanvas(), MapData.MAP_SIZE);
  }

  public void drawImage(BufferedImage image) {
    this.drawImageCraft(MapPalette.imageToBytes(image, ProtocolVersion.MAXIMUM_VERSION));
  }

  public void drawImage(int[] image) {
    this.drawImageCraft(MapPalette.imageToBytes(image, ProtocolVersion.MAXIMUM_VERSION));
  }

  public void drawImageCraft(byte[] craftBytes) {
    for (int i = 0; i < this.canvas.length; ++i) {
      byte color = craftBytes[i];
      if (color != MapPalette.TRANSPARENT) {
        this.canvas[i] = color;
      }
    }
  }

  public void drawImageCraft(int[] craftBytes) {
    for (int i = 0; i < this.canvas.length; ++i) {
      byte color = (byte) craftBytes[i];
      if (color != MapPalette.TRANSPARENT) {
        this.canvas[i] = color;
      }
    }
  }

  public MapData getMapData(MapPalette.MapVersion version) {
    byte[] convertedCanvas = new byte[MapData.MAP_SIZE];
    return new MapData(MapPalette.convertImage(this.canvas, convertedCanvas, version));
  }

  public MapData[] getMaps17Data() {
    MapData[] maps = new MapData[MapData.MAP_DIM_SIZE];
    byte[] fixedCanvas = this.buffer17.get();
    Arrays.fill(fixedCanvas, (byte) 0);
    MapPalette.convertImage(this.canvas, fixedCanvas, MapPalette.MapVersion.MINIMUM_VERSION);

    for (int i = 0; i < MapData.MAP_DIM_SIZE; ++i) {
      byte[] canvas = new byte[MapData.MAP_DIM_SIZE];
      for (int j = 0; j < MapData.MAP_DIM_SIZE; ++j) {
        canvas[j] = fixedCanvas[j * MapData.MAP_DIM_SIZE + i];
      }

      maps[i] = new MapData(i, canvas);
    }

    return maps;
  }

  public byte[] getCanvas() {
    return this.canvas;
  }
}
