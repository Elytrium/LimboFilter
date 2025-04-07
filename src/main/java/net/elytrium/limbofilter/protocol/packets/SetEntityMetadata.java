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

package net.elytrium.limbofilter.protocol.packets;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import java.util.function.Function;
import net.elytrium.limbofilter.protocol.data.EntityMetadata;

public class SetEntityMetadata implements MinecraftPacket {

  private final int entityId;
  private final Function<ProtocolVersion, EntityMetadata> metadata;

  public SetEntityMetadata(int entityId, Function<ProtocolVersion, EntityMetadata> metadata) {
    this.entityId = entityId;
    this.metadata = metadata;
  }

  public SetEntityMetadata(int entityId, EntityMetadata metadata) {
    this(entityId, protocolVersion -> metadata);
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion) {
    throw new IllegalStateException();
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion) {
    if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_7_6) <= 0) {
      buf.writeInt(this.entityId);
    } else {
      ProtocolUtils.writeVarInt(buf, this.entityId);
    }
    this.metadata.apply(protocolVersion).encode(buf, protocolVersion);
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return true;
  }
}
