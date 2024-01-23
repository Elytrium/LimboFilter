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

package net.elytrium.limbofilter.cache;

import com.google.common.primitives.Ints;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ChatType;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import com.velocitypowered.proxy.protocol.packet.chat.SystemChatPacket;
import com.velocitypowered.proxy.protocol.packet.chat.legacy.LegacyChatPacket;
import com.velocitypowered.proxy.protocol.packet.title.GenericTitlePacket;
import java.text.MessageFormat;
import java.util.UUID;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.chunk.Dimension;
import net.elytrium.limboapi.api.chunk.VirtualChunk;
import net.elytrium.limboapi.api.material.Item;
import net.elytrium.limboapi.api.material.VirtualItem;
import net.elytrium.limboapi.api.protocol.PreparedPacket;
import net.elytrium.limboapi.api.protocol.packets.PacketFactory;
import net.elytrium.limbofilter.LimboFilter;
import net.elytrium.limbofilter.Settings;
import net.elytrium.limbofilter.protocol.data.ItemFrame;
import net.elytrium.limbofilter.protocol.packets.SetEntityMetadata;
import net.elytrium.limbofilter.protocol.packets.SpawnEntity;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;

public class CachedPackets {

  private PreparedPacket fallingCheckPackets;
  private PreparedPacket fallingCheckTitleAndChat;
  private PreparedPacket[] captchaAttemptsPacket;
  private PreparedPacket captchaFailed;
  private PreparedPacket fallingCheckFailed;
  private PreparedPacket timesUp;
  private PreparedPacket resetSlot;
  private PreparedPacket kickClientCheckSettings;
  private PreparedPacket kickClientCheckBrand;
  private PreparedPacket kickProxyCheck;
  private PreparedPacket successfulBotFilterChat;
  private PreparedPacket successfulBotFilterDisconnect;
  private PreparedPacket noAbilities;
  private PreparedPacket[] experience;
  private PreparedPacket captchaNotReadyYet;
  private PreparedPacket framedCaptchaPackets;

  public void createPackets(LimboFactory limboFactory, PacketFactory packetFactory) {
    Settings.MAIN.STRINGS strings = Settings.IMP.MAIN.STRINGS;

    this.captchaAttemptsPacket = this.createCaptchaAttemptsPacket(limboFactory, packetFactory, strings.CHECKING_CAPTCHA_TITLE,
        strings.CHECKING_CAPTCHA_SUBTITLE, strings.CHECKING_CAPTCHA_CHAT, strings.CHECKING_WRONG_CAPTCHA_CHAT);
    this.fallingCheckPackets = this.createFallingCheckPackets(limboFactory, packetFactory);
    this.fallingCheckTitleAndChat =
        this.createFallingCheckTitleAndChatPackets(limboFactory, strings.CHECKING_TITLE, strings.CHECKING_SUBTITLE, strings.CHECKING_CHAT);
    this.captchaFailed = this.createDisconnectPacket(limboFactory, strings.CAPTCHA_FAILED_KICK);
    this.fallingCheckFailed = this.createDisconnectPacket(limboFactory, strings.FALLING_CHECK_FAILED_KICK);
    this.timesUp = this.createDisconnectPacket(limboFactory, strings.TIMES_UP);

    this.resetSlot = limboFactory.createPreparedPacket()
      .prepare(
          this.createSetSlotPacketLegacy(
              packetFactory, limboFactory.getItem(Item.AIR), 0, null
          ), ProtocolVersion.MINIMUM_VERSION, ProtocolVersion.MINECRAFT_1_8
      ).prepare(
          this.createSetSlotPacketModern(
              packetFactory, limboFactory.getItem(Item.AIR), 0, null
          ), ProtocolVersion.MINECRAFT_1_9
      )
    .build();

    this.kickClientCheckSettings = this.createDisconnectPacket(limboFactory, strings.CLIENT_SETTINGS_KICK);
    this.kickClientCheckBrand = this.createDisconnectPacket(limboFactory, strings.CLIENT_BRAND_KICK);
    this.kickProxyCheck = this.createDisconnectPacket(limboFactory, strings.PROXY_CHECK_KICK);

    this.successfulBotFilterChat = limboFactory.createPreparedPacket();
    this.createChatPacket(this.successfulBotFilterChat, strings.SUCCESSFUL_CRACKED);
    this.successfulBotFilterChat.build();

    this.successfulBotFilterDisconnect = this.createDisconnectPacket(limboFactory, strings.SUCCESSFUL_PREMIUM_KICK);

    this.noAbilities = this.createAbilitiesPacket(limboFactory, packetFactory);
    this.experience = this.createExpPackets(limboFactory, packetFactory);

    this.captchaNotReadyYet = limboFactory.createPreparedPacket();
    this.createChatPacket(this.captchaNotReadyYet, strings.CAPTCHA_NOT_READY_YET);

    this.framedCaptchaPackets = this.createFramedCaptchaPackets(limboFactory);
  }

  private PreparedPacket createFramedCaptchaPackets(LimboFactory limboFactory) {
    Settings.MAIN.FRAMED_CAPTCHA settings = Settings.IMP.MAIN.FRAMED_CAPTCHA;
    if (!settings.FRAMED_CAPTCHA_ENABLED) {
      return null;
    }

    Settings.MAIN.FRAMED_CAPTCHA.COORDS.OFFSET_1_7 offset = settings.COORDS.OFFSET_1_7;

    PreparedPacket preparedPacket = limboFactory.createPreparedPacket();

    for (int y = 0; y < settings.HEIGHT; y++) {
      for (int x = 0; x < settings.WIDTH; x++) {
        int id = y * settings.WIDTH + x;
        int entityId = id + 10;

        preparedPacket
            .prepare(
                new SpawnEntity(
                    entityId, UUID.nameUUIDFromBytes(Ints.toByteArray(id)), ItemFrame::getID,
                    settings.COORDS.X + x + offset.X, settings.COORDS.Y + y + offset.Y, settings.COORDS.Z + offset.Z,
                    0, 0, 0, 2, 0, 0, 0
                ), ProtocolVersion.MINIMUM_VERSION, ProtocolVersion.MINECRAFT_1_7_6
            )
            .prepare(
                new SpawnEntity(
                    entityId, UUID.nameUUIDFromBytes(Ints.toByteArray(id)), ItemFrame::getID,
                    settings.COORDS.X + x, settings.COORDS.Y + y, settings.COORDS.Z,
                    0, 180, 180, 2, 0, 0, 0
                ), ProtocolVersion.MINECRAFT_1_8
            )
            .prepare(
                new SetEntityMetadata(
                    entityId, version -> ItemFrame.createMapMetadata(limboFactory, version, id)
                ), ProtocolVersion.MINIMUM_VERSION
            );
      }
    }

    return preparedPacket.build();
  }

  private PreparedPacket[] createCaptchaAttemptsPacket(LimboFactory limboFactory, PacketFactory packetFactory,
                                                       String checkingTitle, String checkingSubtitle, String checkingChat, String wrongCaptcha) {
    PreparedPacket[] packets = new PreparedPacket[Settings.IMP.MAIN.CAPTCHA_ATTEMPTS + 1];

    for (int i = 1; i < Settings.IMP.MAIN.CAPTCHA_ATTEMPTS; ++i) {
      PreparedPacket packet = limboFactory.createPreparedPacket();
      this.createChatPacket(packet, MessageFormat.format(wrongCaptcha, i));

      if (!Settings.IMP.MAIN.FRAMED_CAPTCHA.FRAMED_CAPTCHA_ENABLED) {
        packet
            .prepare(
                this.createSetSlotPacketLegacy(
                    packetFactory, limboFactory.getItem(Item.FILLED_MAP), 1, null
                ), ProtocolVersion.MINIMUM_VERSION, ProtocolVersion.MINECRAFT_1_8
            ).prepare(
                this.createSetSlotPacketModern(
                    packetFactory, limboFactory.getItem(Item.FILLED_MAP), 1, null
                ), ProtocolVersion.MINECRAFT_1_9, ProtocolVersion.MINECRAFT_1_16_4
            ).prepare(
                this.createSetSlotPacketModern(
                    packetFactory, limboFactory.getItem(Item.FILLED_MAP), 1,
                    CompoundBinaryTag.builder().put("map", IntBinaryTag.intBinaryTag(0)).build()
                ), ProtocolVersion.MINECRAFT_1_17
        );
      }
      packets[i] = packet.build();
    }

    packets[Settings.IMP.MAIN.CAPTCHA_ATTEMPTS] = this.createCaptchaFirstAttemptPacket(limboFactory, checkingTitle, checkingSubtitle, checkingChat);
    if (!Settings.IMP.MAIN.FRAMED_CAPTCHA.FRAMED_CAPTCHA_ENABLED) {
      packets[Settings.IMP.MAIN.CAPTCHA_ATTEMPTS]
          .prepare(
              this.createSetSlotPacketLegacy(
                  packetFactory, limboFactory.getItem(Item.FILLED_MAP), 1, null
              ), ProtocolVersion.MINIMUM_VERSION, ProtocolVersion.MINECRAFT_1_8
          ).prepare(
              this.createSetSlotPacketModern(
                  packetFactory, limboFactory.getItem(Item.FILLED_MAP), 1, null
              ), ProtocolVersion.MINECRAFT_1_9, ProtocolVersion.MINECRAFT_1_16_4
          ).prepare(
              this.createSetSlotPacketModern(
                  packetFactory, limboFactory.getItem(Item.FILLED_MAP), 1,
                  CompoundBinaryTag.builder().put("map", IntBinaryTag.intBinaryTag(0)).build()
              ), ProtocolVersion.MINECRAFT_1_17
      );
    }
    packets[Settings.IMP.MAIN.CAPTCHA_ATTEMPTS].build();

    return packets;
  }

  public void dispose() {
    this.singleDispose(this.fallingCheckPackets);
    this.singleDispose(this.fallingCheckTitleAndChat);
    this.singleDispose(this.captchaAttemptsPacket);
    this.singleDispose(this.captchaFailed);
    this.singleDispose(this.fallingCheckFailed);
    this.singleDispose(this.timesUp);
    this.singleDispose(this.resetSlot);
    this.singleDispose(this.kickClientCheckBrand);
    this.singleDispose(this.kickClientCheckSettings);
    this.singleDispose(this.kickProxyCheck);
    this.singleDispose(this.successfulBotFilterChat);
    this.singleDispose(this.successfulBotFilterDisconnect);
    this.singleDispose(this.noAbilities);
    this.singleDispose(this.experience);
    this.singleDispose(this.captchaNotReadyYet);
    this.singleDispose(this.framedCaptchaPackets);
  }

  private void singleDispose(PreparedPacket packet) {
    if (packet != null) {
      packet.release();
    }
  }

  private void singleDispose(PreparedPacket[] packets) {
    if (packets != null) {
      for (PreparedPacket packet : packets) {
        this.singleDispose(packet);
      }
    }
  }

  private PreparedPacket createCaptchaFirstAttemptPacket(LimboFactory factory, String checkingTitle, String checkingSubtitle, String checkingChat) {
    PreparedPacket preparedPacket = factory.createPreparedPacket();
    this.createChatPacket(preparedPacket, MessageFormat.format(checkingChat, Settings.IMP.MAIN.CAPTCHA_ATTEMPTS));

    if (!checkingTitle.isEmpty() && !checkingSubtitle.isEmpty()) {
      this.createTitlePacket(
          preparedPacket,
          MessageFormat.format(checkingTitle, Settings.IMP.MAIN.CAPTCHA_ATTEMPTS),
          MessageFormat.format(checkingSubtitle, Settings.IMP.MAIN.CAPTCHA_ATTEMPTS)
      );
    }

    return preparedPacket;
  }

  private PreparedPacket createFallingCheckPackets(LimboFactory limboFactory, PacketFactory packetFactory) {
    Settings.MAIN.FALLING_COORDS fallingCoords = Settings.IMP.MAIN.FALLING_COORDS;

    Settings.MAIN.COORDS coords = Settings.IMP.MAIN.COORDS;
    PreparedPacket preparedPacket = limboFactory.createPreparedPacket().prepare(
        this.createPlayerPosAndLook(
            packetFactory,
            fallingCoords.X, fallingCoords.Y, fallingCoords.Z,
            (float) coords.FALLING_CHECK_YAW, (float) coords.FALLING_CHECK_PITCH
        )
    ).prepare(this.createChunkData(
        limboFactory, packetFactory, fallingCoords.X >> 4, fallingCoords.Z >> 4, Settings.IMP.MAIN.BOTFILTER_DIMENSION
    )).prepare(this.createUpdateViewPosition(packetFactory, fallingCoords.X, fallingCoords.Z), ProtocolVersion.MINECRAFT_1_14);

    return preparedPacket.build();
  }

  private PreparedPacket createFallingCheckTitleAndChatPackets(LimboFactory limboFactory,
                                                               String checkingTitle, String checkingSubtitle, String checkingChat) {
    if ((checkingTitle.isEmpty() || checkingSubtitle.isEmpty()) && checkingChat.isEmpty()) {
      return null;
    }

    PreparedPacket preparedPacket = limboFactory.createPreparedPacket();
    if (!checkingTitle.isEmpty() && !checkingSubtitle.isEmpty()) {
      this.createTitlePacket(preparedPacket, checkingTitle, checkingSubtitle);
    }

    if (!checkingChat.isEmpty()) {
      this.createChatPacket(preparedPacket, checkingChat);
    }

    return preparedPacket.build();
  }

  private MinecraftPacket createChunkData(LimboFactory limboFactory, PacketFactory packetFactory, int chunkX, int chunkZ, Dimension dimension) {
    VirtualChunk chunk = limboFactory.createVirtualChunk(chunkX, chunkZ, dimension.getDefaultBiome());
    return (MinecraftPacket) packetFactory.createChunkDataPacket(chunk.getFullChunkSnapshot(), dimension);
  }

  private MinecraftPacket createPlayerPosAndLook(PacketFactory factory, double x, double y, double z, float yaw, float pitch) {
    return (MinecraftPacket) factory.createPositionRotationPacket(x, y, z, yaw, pitch, false, 44, true);
  }

  private MinecraftPacket createUpdateViewPosition(PacketFactory factory, int x, int z) {
    return (MinecraftPacket) factory.createUpdateViewPositionPacket(x >> 4, z >> 4);
  }

  private PreparedPacket createAbilitiesPacket(LimboFactory limboFactory, PacketFactory packetFactory) {
    return limboFactory.createPreparedPacket().prepare(packetFactory.createPlayerAbilitiesPacket((byte) 6, 0f, 0f)).build();
  }

  private PreparedPacket[] createExpPackets(LimboFactory limboFactory, PacketFactory packetFactory) {
    int ticks = Settings.IMP.MAIN.FALLING_CHECK_TICKS;
    PreparedPacket[] packets = new PreparedPacket[ticks];
    float expInterval = 0.01F;
    for (int i = 0; i < ticks; ++i) {
      int percentage = i * 100 / ticks;
      packets[i] =
          limboFactory.createPreparedPacket().prepare(packetFactory.createSetExperiencePacket(percentage * expInterval, percentage, 0)).build();
    }

    return packets;
  }

  private MinecraftPacket createSetSlotPacketLegacy(PacketFactory packetFactory, VirtualItem item, int count, CompoundBinaryTag nbt) {
    return (MinecraftPacket) packetFactory.createSetSlotPacket(0, 36, item, count, 0, nbt);
  }

  private MinecraftPacket createSetSlotPacketModern(PacketFactory packetFactory, VirtualItem item, int count, CompoundBinaryTag nbt) {
    return (MinecraftPacket) packetFactory.createSetSlotPacket(0, Settings.IMP.MAIN.CAPTCHA_LEFT_HAND ? 45 : 36, item, count, 0, nbt);
  }

  public void createChatPacket(PreparedPacket packet, String text) {
    packet
        .prepare(new LegacyChatPacket(
            ProtocolUtils.getJsonChatSerializer(ProtocolVersion.MINIMUM_VERSION).serialize(
                LimboFilter.getSerializer().deserialize(text)
            ), LegacyChatPacket.CHAT_TYPE, null
        ), ProtocolVersion.MINIMUM_VERSION, ProtocolVersion.MINECRAFT_1_15_2)
        .prepare(new LegacyChatPacket(
            ProtocolUtils.getJsonChatSerializer(ProtocolVersion.MINECRAFT_1_16).serialize(
                LimboFilter.getSerializer().deserialize(text)
            ), LegacyChatPacket.CHAT_TYPE, null
        ), ProtocolVersion.MINECRAFT_1_16, ProtocolVersion.MINECRAFT_1_18_2)
        .prepare(version -> new SystemChatPacket(
            new ComponentHolder(version, LimboFilter.getSerializer().deserialize(text)), ChatType.SYSTEM
        ), ProtocolVersion.MINECRAFT_1_19);
  }

  private PreparedPacket createDisconnectPacket(LimboFactory factory, String message) {
    return factory.createPreparedPacket().prepare(version ->
        DisconnectPacket.create(LimboFilter.getSerializer().deserialize(message), version, false)).build();
  }

  public void createTitlePacket(PreparedPacket preparedPacket, String title, String subtitle) {
    preparedPacket.prepare(version -> {
      GenericTitlePacket packet = GenericTitlePacket.constructTitlePacket(GenericTitlePacket.ActionType.SET_TITLE, version);
      packet.setComponent(new ComponentHolder(version, LimboFilter.getSerializer().deserialize(title)));
      return packet;
    }, ProtocolVersion.MINECRAFT_1_8);

    if (!subtitle.isEmpty()) {
      preparedPacket.prepare(version -> {
        GenericTitlePacket packet = GenericTitlePacket.constructTitlePacket(GenericTitlePacket.ActionType.SET_SUBTITLE, version);
        packet.setComponent(new ComponentHolder(version, LimboFilter.getSerializer().deserialize(subtitle)));
        return packet;
      }, ProtocolVersion.MINECRAFT_1_8);
    }

    if (!subtitle.isEmpty() && !title.isEmpty()) {
      preparedPacket.prepare(version -> {
        GenericTitlePacket packet = GenericTitlePacket.constructTitlePacket(GenericTitlePacket.ActionType.SET_TIMES, version);
        packet.setFadeIn(10);
        packet.setStay(70);
        packet.setFadeOut(20);
        return packet;
      }, ProtocolVersion.MINECRAFT_1_8);
    }
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

  public PreparedPacket getResetSlot() {
    return this.resetSlot;
  }

  public PreparedPacket getKickClientCheckSettings() {
    return this.kickClientCheckSettings;
  }

  public PreparedPacket getKickClientCheckBrand() {
    return this.kickClientCheckBrand;
  }

  public PreparedPacket getKickProxyCheck() {
    return this.kickProxyCheck;
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

  public PreparedPacket getExperience(int tick) {
    return this.experience[tick];
  }

  public PreparedPacket getLastExperience() {
    return this.experience[this.experience.length - 1];
  }

  public PreparedPacket getFallingCheckPackets() {
    return this.fallingCheckPackets;
  }

  public PreparedPacket getCaptchaAttemptsPacket(int attempt) {
    return this.captchaAttemptsPacket[attempt];
  }

  public PreparedPacket getCaptchaNotReadyYet() {
    return this.captchaNotReadyYet;
  }

  public PreparedPacket getFallingCheckTitleAndChat() {
    return this.fallingCheckTitleAndChat;
  }

  public PreparedPacket getFramedCaptchaPackets() {
    return this.framedCaptchaPackets;
  }
}
