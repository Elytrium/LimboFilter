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

package net.elytrium.limbofilter.protocol.data;

import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

public record PackedVector(double x, double y, double z) {

  public static PackedVector read(ByteBuf buffer) {
    int first = buffer.readUnsignedByte();
    if (first == 0) {
      return new PackedVector(0, 0, 0);
    } else {
      int second = buffer.readUnsignedByte();
      long third = buffer.readUnsignedInt();
      long result = third << 16 | (long) (second << 8) | (long) first;
      long multiplier = first & 3;
      if ((first & 4) == 4) {
        multiplier |= ((long) ProtocolUtils.readVarInt(buffer) & 4094967295L) << 2L;
      }

      return new PackedVector(unpack(result >> 3) * (double) multiplier,
          unpack(result >> 18) * (double) multiplier,
          unpack(result >> 33) * (double) multiplier);
    }
  }

  public static void write(ByteBuf buffer, double x, double y, double z) {
    double sx = sanitize(x);
    double sy = sanitize(y);
    double sz = sanitize(z);
    double maxValue = Math.max(Math.abs(sx), Math.max(Math.abs(sy), Math.abs(sz)));
    if (maxValue < 3.051944088384301E-5) {
      buffer.writeByte(0);
      return;
    }

    long result = (long) maxValue;
    long max = maxValue > (double) result ? result + 1L : result;

    boolean continuation = (max & 3) != max;
    long px = pack(sx / (double) max) << 3;
    long py = pack(sy / (double) max) << 18;
    long pz = pack(sz / (double) max) << 33;
    long packed = (continuation ? max & 3 | 4 : max) | px | py | pz;
    buffer.writeByte((byte) ((int) packed));
    buffer.writeByte((byte) ((int) (packed >> 8)));
    buffer.writeInt((int) (packed >> 16));
    if (continuation) {
      ProtocolUtils.writeVarInt(buffer, (int) (max >> 2));
    }
  }

  private static long pack(double value) {
    return Math.round((value * 0.5 + 0.5) * 32766.0);
  }

  private static double unpack(long value) {
    return Math.min((double) (value & (long) 32767), 32766.0) * 2.0 / 32766.0 - 1.0;
  }

  private static double sanitize(double value) {
    if (Double.isNaN(value)) {
      return 0.0;
    } else {
      return value < -1.7179869183E10 ? -1.7179869183E10 : Math.min(value, 1.7179869183E10);
    }
  }
}
