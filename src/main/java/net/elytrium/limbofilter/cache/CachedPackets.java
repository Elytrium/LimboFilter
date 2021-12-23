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

package net.elytrium.limbofilter.cache;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.Chat;
import com.velocitypowered.proxy.protocol.packet.Disconnect;
import com.velocitypowered.proxy.protocol.packet.title.GenericTitlePacket;
import java.util.ArrayList;
import java.util.List;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.material.Item;
import net.elytrium.limboapi.api.material.VirtualItem;
import net.elytrium.limboapi.api.protocol.PreparedPacket;
import net.elytrium.limboapi.api.protocol.packets.BuiltInPackets;
import net.elytrium.limbofilter.Settings;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class CachedPackets {

  private PreparedPacket captchaFailed;
  private PreparedPacket fallingCheckFailed;
  private PreparedPacket timesUp;
  private PreparedPacket setSlot;
  private PreparedPacket resetSlot;
  private PreparedPacket checkingChat;
  private PreparedPacket checkingTitle;
  private PreparedPacket kickClientCheckSettings;
  private PreparedPacket kickClientCheckBrand;
  private PreparedPacket successfulBotFilterChat;
  private PreparedPacket successfulBotFilterDisconnect;
  private PreparedPacket noAbilities;
  private List<PreparedPacket> experience;

  public void createPackets(LimboFactory factory) {
    Settings.MAIN.STRINGS strings = Settings.IMP.MAIN.STRINGS;

    this.captchaFailed = this.createDisconnectPacket(factory, strings.CAPTCHA_FAILED_KICK);
    this.fallingCheckFailed = this.createDisconnectPacket(factory, strings.FALLING_CHECK_FAILED_KICK);
    this.timesUp = this.createDisconnectPacket(factory, strings.TIMES_UP);

    this.setSlot = factory.createPreparedPacket()
        .prepare(
            this.createSetSlotPacket(
                factory, factory.getItem(Item.FILLED_MAP), 1, null
            ), ProtocolVersion.MINIMUM_VERSION, ProtocolVersion.MINECRAFT_1_16_4
        ).prepare(
            this.createSetSlotPacket(
                factory, factory.getItem(Item.FILLED_MAP), 1, CompoundBinaryTag.builder().put("map", IntBinaryTag.of(0)).build()
            ), ProtocolVersion.MINECRAFT_1_17
        );

    this.resetSlot = factory.createPreparedPacket().prepare(this.createSetSlotPacket(factory, factory.getItem(Item.AIR), 0, null));
    this.checkingChat = this.createChatPacket(factory, strings.CHECKING_CHAT);
    this.checkingTitle = this.createTitlePacket(factory, strings.CHECKING_TITLE, strings.CHECKING_SUBTITLE);

    this.kickClientCheckSettings = this.createDisconnectPacket(factory, strings.CLIENT_SETTINGS_KICK);
    this.kickClientCheckBrand = this.createDisconnectPacket(factory, strings.CLIENT_BRAND_KICK);

    this.successfulBotFilterChat = this.createChatPacket(factory, strings.SUCCESSFUL_CRACKED);
    this.successfulBotFilterDisconnect = this.createDisconnectPacket(factory, strings.SUCCESSFUL_PREMIUM_KICK);

    this.noAbilities = this.createAbilitiesPacket(factory);
    this.experience = this.createExpPackets(factory);
  }

  private PreparedPacket createAbilitiesPacket(LimboFactory factory) {
    return factory.createPreparedPacket().prepare(factory.instantiatePacket(BuiltInPackets.PlayerAbilities, (byte) 6, 0f, 0f));
  }

  private List<PreparedPacket> createExpPackets(LimboFactory factory) {
    List<PreparedPacket> packets = new ArrayList<>();
    long ticks = Settings.IMP.MAIN.FALLING_CHECK_TICKS;
    float expInterval = 0.01F;
    for (int i = 0; i < ticks; ++i) {
      int percentage = (int) (i * 100 / ticks);
      packets.add(
          factory.createPreparedPacket().prepare(factory.instantiatePacket(BuiltInPackets.SetExperience, percentage * expInterval, percentage, 0))
      );
    }

    return packets;
  }

  private MinecraftPacket createSetSlotPacket(LimboFactory factory, VirtualItem item, int count, CompoundBinaryTag nbt) {
    return (MinecraftPacket) factory.instantiatePacket(BuiltInPackets.SetSlot, 0, 36, item, count, 0, nbt);
  }

  public PreparedPacket createChatPacket(LimboFactory factory, String text) {
    return factory.createPreparedPacket()
        .prepare(new Chat(
            ProtocolUtils.getJsonChatSerializer(ProtocolVersion.MINIMUM_VERSION).serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(text)
            ), Chat.CHAT_TYPE, null
        ), ProtocolVersion.MINIMUM_VERSION, ProtocolVersion.MINECRAFT_1_15_2)
        .prepare(new Chat(
            ProtocolUtils.getJsonChatSerializer(ProtocolVersion.MINECRAFT_1_16).serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(text)
            ), Chat.CHAT_TYPE, null
        ), ProtocolVersion.MINECRAFT_1_16);
  }

  private PreparedPacket createDisconnectPacket(LimboFactory factory, String message) {
    return factory.createPreparedPacket().prepare(version -> Disconnect.create(LegacyComponentSerializer.legacyAmpersand().deserialize(message), version));
  }

  public PreparedPacket createTitlePacket(LimboFactory factory, String title, String subtitle) {
    PreparedPacket preparedPacket = factory.createPreparedPacket();

    Component titleComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(title);

    preparedPacket.prepare((version) -> {
      GenericTitlePacket packet = GenericTitlePacket.constructTitlePacket(GenericTitlePacket.ActionType.SET_TITLE, version);
      packet.setComponent(ProtocolUtils.getJsonChatSerializer(version).serialize(titleComponent));
      return packet;
    }, ProtocolVersion.MINECRAFT_1_8);

    if (!subtitle.isEmpty()) {
      Component subtitleComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle);

      preparedPacket.prepare((version) -> {
        GenericTitlePacket packet = GenericTitlePacket.constructTitlePacket(GenericTitlePacket.ActionType.SET_SUBTITLE, version);
        packet.setComponent(ProtocolUtils.getJsonChatSerializer(version).serialize(subtitleComponent));
        return packet;
      }, ProtocolVersion.MINECRAFT_1_8);
    }

    if (!subtitle.isEmpty() && !title.isEmpty()) {
      preparedPacket.prepare((version) -> {
        GenericTitlePacket packet = GenericTitlePacket.constructTitlePacket(GenericTitlePacket.ActionType.SET_TIMES, version);
        packet.setFadeIn(10);
        packet.setStay(70);
        packet.setFadeOut(20);
        return packet;
      }, ProtocolVersion.MINECRAFT_1_8);
    }

    return preparedPacket;
  }

  public PreparedPacket getCaptchaFailed() {
    return this.captchaFailed;
  }

  public PreparedPacket getFallingCheckFailed() {
    return this.fallingCheckFailed;
  }

  public PreparedPacket getTimesUp() {
    return this.timesUp;
  }

  public PreparedPacket getSetSlot() {
    return this.setSlot;
  }

  public PreparedPacket getResetSlot() {
    return this.resetSlot;
  }

  public PreparedPacket getCheckingChat() {
    return this.checkingChat;
  }

  public PreparedPacket getCheckingTitle() {
    return this.checkingTitle;
  }

  public PreparedPacket getKickClientCheckSettings() {
    return this.kickClientCheckSettings;
  }

  public PreparedPacket getKickClientCheckBrand() {
    return this.kickClientCheckBrand;
  }

  public PreparedPacket getSuccessfulBotFilterChat() {
    return this.successfulBotFilterChat;
  }

  public PreparedPacket getSuccessfulBotFilterDisconnect() {
    return this.successfulBotFilterDisconnect;
  }

  public PreparedPacket getNoAbilities() {
    return this.noAbilities;
  }

  public List<PreparedPacket> getExperience() {
    return this.experience;
  }
}
