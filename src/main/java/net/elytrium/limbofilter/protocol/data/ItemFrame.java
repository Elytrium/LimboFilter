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
import java.util.Map;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.material.Item;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;

public class ItemFrame {

  public static int getID(ProtocolVersion protocolVersion) {
    if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_13_2) <= 0) {
      return 71;
    } else if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_14_4) <= 0) {
      return 35;
    } else if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_15_2) <= 0) {
      return 36;
    } else if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_16_4) <= 0) {
      return 38;
    } else if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_18_2) <= 0) {
      return 42;
    } else if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_19_1) <= 0) {
      return 45;
    } else if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_19_3) <= 0) {
      return 46;
    } else if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_20_2) <= 0) {
      return 56;
    } else if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_20_3) <= 0) {
      return 57;
    } else {
      return 60;
    }
  }

  public static byte getMetadataIndex(ProtocolVersion protocolVersion) {
    if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_7_6) <= 0) {
      return 2;
    } else if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_8) <= 0) {
      return 8;
    } else if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_9_4) <= 0) {
      return 5;
    } else if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_13_2) <= 0) {
      return 6;
    } else if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_16_4) <= 0) {
      return 7;
    } else {
      return 8;
    }
  }

  public static EntityMetadata createMapMetadata(LimboFactory limboFactory, ProtocolVersion protocolVersion, int mapId) {
    if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_12_2) <= 0) {
      return new EntityMetadata(Map.of(
          getMetadataIndex(protocolVersion), new EntityMetadata.SlotEntry(limboFactory.getItem(Item.FILLED_MAP), 1, mapId, null, null)
      ));
    } else {
      return new EntityMetadata(Map.of(
          getMetadataIndex(protocolVersion), new EntityMetadata.SlotEntry(limboFactory.getItem(Item.FILLED_MAP), 1, 0,
              CompoundBinaryTag.builder().put("map", IntBinaryTag.intBinaryTag(mapId)).build(),
              limboFactory.createItemComponentMap().add(ProtocolVersion.MINECRAFT_1_20_5, "minecraft:map_id", mapId))
      ));
    }
  }

  public static EntityMetadata createRotationMetadata(ProtocolVersion protocolVersion, int rotation) {
    if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_8) <= 0) {
      return new EntityMetadata(Map.of(
          (byte) (getMetadataIndex(protocolVersion) + 1), new EntityMetadata.ByteEntry(rotation % 4)
      ));
    } else {
      return new EntityMetadata(Map.of(
          (byte) (getMetadataIndex(protocolVersion) + 1), new EntityMetadata.VarIntEntry(rotation)
      ));
    }
  }
}
