/*
 * Copyright (C) 2021 - 2023 Elytrium
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

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import net.elytrium.limboapi.api.material.VirtualItem;
import net.kyori.adventure.nbt.CompoundBinaryTag;

public class EntityMetadata {

  public interface Entry {

    void encode(ByteBuf buf, ProtocolVersion protocolVersion);

    int getType(ProtocolVersion protocolVersion);
  }

  public static class SlotEntry implements Entry {

    private final boolean present;
    private final VirtualItem item;
    private final int count;
    private final int data;
    private final CompoundBinaryTag nbt;

    public SlotEntry(boolean present, VirtualItem item, int count, int data, CompoundBinaryTag nbt) {
      this.present = present;
      this.item = item;
      this.count = count;
      this.data = data;
      this.nbt = nbt;
    }

    public SlotEntry(VirtualItem item, int count, int data, CompoundBinaryTag nbt) {
      this(true, item, count, data, nbt);
    }

    public SlotEntry() {
      this(false, null, 0, 0, null);
    }

    @Override
    public void encode(ByteBuf buf, ProtocolVersion protocolVersion) {
      if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_13_2) >= 0) {
        buf.writeBoolean(this.present);
      }

      if (!this.present && protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_13_2) < 0) {
        buf.writeShort(-1);
      }

      if (this.present) {
        if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_13_2) < 0) {
          buf.writeShort(this.item.getID(protocolVersion));
        } else {
          ProtocolUtils.writeVarInt(buf, this.item.getID(protocolVersion));
        }
        buf.writeByte(this.count);
        if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_13) < 0) {
          buf.writeShort(this.data);
        }

        if (this.nbt == null) {
          if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_8) < 0) {
            buf.writeShort(-1);
          } else {
            buf.writeByte(0);
          }
        } else {
          ProtocolUtils.writeBinaryTag(buf, protocolVersion, this.nbt);
        }
      }
    }

    @Override
    public int getType(ProtocolVersion protocolVersion) {
      if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_12_2) <= 0) {
        return 5;
      } else if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_19_1) <= 0) {
        return 6;
      } else {
        return 7;
      }
    }
  }

  public static class VarIntEntry implements Entry {

    private final int value;

    public VarIntEntry(int value) {
      this.value = value;
    }

    @Override
    public void encode(ByteBuf buf, ProtocolVersion protocolVersion) {
      if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_8) <= 0) {
        buf.writeInt(this.value);
      } else {
        ProtocolUtils.writeVarInt(buf, this.value);
      }
    }

    @Override
    public int getType(ProtocolVersion protocolVersion) {
      if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_8) <= 0) {
        return 2;
      } else {
        return 1;
      }
    }
  }

  public static class ByteEntry implements Entry {

    private final int value;

    public ByteEntry(int value) {
      this.value = value;
    }

    @Override
    public void encode(ByteBuf buf, ProtocolVersion protocolVersion) {
      buf.writeByte(this.value);
    }

    @Override
    public int getType(ProtocolVersion protocolVersion) {
      return 0;
    }
  }

  private final Map<Byte, Entry> entries;

  public EntityMetadata(Map<Byte, Entry> entries) {
    this.entries = entries;
  }

  public void encode(ByteBuf buf, ProtocolVersion protocolVersion) {
    if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_8) <= 0) {
      this.entries.forEach((index, value) -> {
        buf.writeByte((index & 0x1F) | (value.getType(protocolVersion) << 5));
        value.encode(buf, protocolVersion);
      });
      buf.writeByte(0x7F);
    } else {
      this.entries.forEach((index, value) -> {
        buf.writeByte(index);
        ProtocolUtils.writeVarInt(buf, value.getType(protocolVersion));
        value.encode(buf, protocolVersion);
      });
      buf.writeByte(0xFF);
    }
  }
}
