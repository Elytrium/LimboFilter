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

package net.elytrium.limbofilter.protocol.packets;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

public class Interact implements MinecraftPacket {

  private int entityId;
  private int type;
  private float targetX;
  private float targetY;
  private float targetZ;
  private int hand;
  private boolean sneaking;

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion) {
    if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_7_6) > 0) {
      this.entityId = ProtocolUtils.readVarInt(buf);
      this.type = ProtocolUtils.readVarInt(buf);
      if (this.type == 2) {
        this.targetX = buf.readFloat();
        this.targetY = buf.readFloat();
        this.targetZ = buf.readFloat();
      }
      if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_8) > 0) {
        if (this.type == 0 || this.type == 2) {
          this.hand = ProtocolUtils.readVarInt(buf);
        }
        if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_15_2) > 0) {
          this.sneaking = buf.readBoolean();
        }
      }
    } else {
      this.entityId = buf.readInt();
      this.type = buf.readByte();
    }
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion) {
    throw new IllegalStateException();
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    handler.handleGeneric(this);
    return true;
  }

  public int getEntityId() {
    return this.entityId;
  }

  public int getType() {
    return this.type;
  }

  public float getTargetX() {
    return this.targetX;
  }

  public float getTargetY() {
    return this.targetY;
  }

  public float getTargetZ() {
    return this.targetZ;
  }

  public int getHand() {
    return this.hand;
  }

  public boolean isSneaking() {
    return this.sneaking;
  }
}
