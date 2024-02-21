/*
 * Copyright (C) 2022-2023 Elytrium
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.elytrium.limbofilter.protocol.packets;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import java.util.function.Function;

public final class SpawnEntityPacket implements MinecraftPacket {

  private final int id;
  private final UUID uuid;
  private final Function<ProtocolVersion, Integer> type;
  private final double positionX;
  private final double positionY;
  private final double positionZ;
  private final float pitch;
  private final float yaw;
  private final float headYaw;
  private final int data;
  private final float velocityX;
  private final float velocityY;
  private final float velocityZ;

  public SpawnEntityPacket(int id, UUID uuid, Function<ProtocolVersion, Integer> type, double positionX, double positionY, double positionZ,
                     float pitch, float yaw, float headYaw, int data, float velocityX, float velocityY, float velocityZ) {
    this.id = id;
    this.uuid = uuid;
    this.type = type;
    this.positionX = positionX;
    this.positionY = positionY;
    this.positionZ = positionZ;
    this.pitch = pitch;
    this.yaw = yaw;
    this.headYaw = headYaw;
    this.data = data;
    this.velocityX = velocityX;
    this.velocityY = velocityY;
    this.velocityZ = velocityZ;
  }

  public SpawnEntityPacket() {
    throw new IllegalStateException();
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion) {
    throw new IllegalStateException();
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion) {
    ProtocolUtils.writeVarInt(buf, this.id);
    if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_8) > 0) {
      ProtocolUtils.writeUuid(buf, this.uuid);
      if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_13_2) > 0) {
        ProtocolUtils.writeVarInt(buf, this.type.apply(protocolVersion));
      } else {
        buf.writeByte(this.type.apply(protocolVersion));
      }
      buf.writeDouble(this.positionX);
      buf.writeDouble(this.positionY);
      buf.writeDouble(this.positionZ);
    } else {
      buf.writeByte(this.type.apply(protocolVersion));
      buf.writeInt((int) (this.positionX * 32.0));
      buf.writeInt((int) (this.positionY * 32.0));
      buf.writeInt((int) (this.positionZ * 32.0));
    }
    buf.writeByte((int) (this.pitch * (256.0F / 360.0F)));
    buf.writeByte((int) (this.yaw * (256.0F / 360.0F)));
    if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_18_2) > 0) {
      buf.writeByte((int) (this.headYaw * (256.0F / 360.0F)));
      ProtocolUtils.writeVarInt(buf, this.data);
    } else {
      buf.writeInt(this.data);
    }
    buf.writeShort((int) (this.velocityX * 8000.0F));
    buf.writeShort((int) (this.velocityY * 8000.0F));
    buf.writeShort((int) (this.velocityZ * 8000.0F));
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return true;
  }
}
