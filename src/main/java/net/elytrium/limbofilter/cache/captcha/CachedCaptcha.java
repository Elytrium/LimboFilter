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

package net.elytrium.limbofilter.cache.captcha;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.protocol.PreparedPacket;
import net.elytrium.limboapi.api.protocol.packets.data.MapPalette;
import net.elytrium.limbofilter.LimboFilter;
import net.elytrium.limbofilter.Settings;
import net.elytrium.limbofilter.captcha.CaptchaHolder;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

public class CachedCaptcha {
  private final LimboFilter plugin;
  private final AtomicInteger threadIdCounter = new AtomicInteger(0);
  private final ThreadLocal<Integer> threadId = ThreadLocal.withInitial(this.threadIdCounter::getAndIncrement);
  private final CaptchaHolder[] firstHolders;
  private final CaptchaHolder[] lastHolders;

  private CaptchaHolder firstHolder;
  @MonotonicNonNull
  private CaptchaHolder lastHolder;
  private ThreadLocal<CaptchaHolder> captchaIterator;
  private boolean disposed;

  public CachedCaptcha(LimboFilter plugin, int threadsCount) {
    this.plugin = plugin;
    this.firstHolders = new CaptchaHolder[threadsCount];
    this.lastHolders = new CaptchaHolder[threadsCount];
  }

  public void addCaptchaPacket(String answer, MinecraftPacket[] mapDataPackets17, Function<MapPalette.MapVersion, MinecraftPacket> mapDataPacket) {
    // It takes time to stop the generator thread, so we're stopping adding new packets there too.
    if (this.disposed) {
      return;
    }

    int threadId = this.threadId.get();

    boolean isFirst = this.firstHolders[threadId] == null;
    CaptchaHolder holder = this.getCaptchaHolder(answer, this.firstHolders[threadId], mapDataPackets17, mapDataPacket);
    this.firstHolders[threadId] = holder;

    if (isFirst) {
      this.lastHolders[threadId] = holder;
    }
  }

  private CaptchaHolder getCaptchaHolder(String answer, CaptchaHolder next, MinecraftPacket[] mapDataPackets17,
                                         Function<MapPalette.MapVersion, MinecraftPacket> mapDataPacket) {
    EnumMap<ProtocolVersion, MinecraftPacket[]> mapDataPacketEnum = new EnumMap<>(ProtocolVersion.class);
    LimboFactory limboFactory = this.plugin.getLimboFactory();
    for (MapPalette.MapVersion version : MapPalette.MapVersion.values()) {
      if (version.getVersions().stream().anyMatch(protocolVersion ->
          limboFactory.getPrepareMinVersion().compareTo(protocolVersion) <= 0 && limboFactory.getPrepareMaxVersion().compareTo(protocolVersion) >= 0)) {
        MinecraftPacket packet = mapDataPacket.apply(version);
        version.getVersions().forEach(protocolVersion -> mapDataPacketEnum.put(protocolVersion, new MinecraftPacket[]{packet}));
      }
    }

    if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.PREPARE_CAPTCHA_PACKETS) {
      PreparedPacket prepared = this.plugin.getLimboFactory().createPreparedPacket();
      return new CaptchaHolder(answer, next, prepared
          .prepare(mapDataPackets17, ProtocolVersion.MINECRAFT_1_7_2, ProtocolVersion.MINECRAFT_1_7_6)
          .prepare(key -> mapDataPacketEnum.get(key)[0], ProtocolVersion.MINECRAFT_1_8)
          .build()
      );
    } else {
      return new CaptchaHolder(answer, next, mapDataPackets17, mapDataPacketEnum);
    }
  }

  public void build() {
    int lastHolderIndex = this.firstHolders.length - 1;
    for (int i = 0; i < lastHolderIndex; ) {
      this.lastHolders[i].setNext(this.firstHolders[++i]);
    }

    this.firstHolder = this.firstHolders[0];
    this.lastHolder = this.lastHolders[lastHolderIndex];
    this.lastHolder.setNext(this.firstHolder);
    this.threadIdCounter.set(0);

    this.captchaIterator = ThreadLocal.withInitial(() -> new CaptchaHolder(this.firstHolders[this.threadId.get() % this.firstHolders.length]));
  }

  public CaptchaHolder getNextCaptcha() {
    if (this.captchaIterator == null) {
      return null;
    } else {
      CaptchaHolder holder = this.captchaIterator.get();
      this.captchaIterator.set(holder.getNext());
      return holder;
    }
  }

  public void dispose() {
    this.disposed = true;
    if (this.firstHolder == null) {
      for (CaptchaHolder holder : this.firstHolders) {
        CaptchaHolder next = holder;
        do {
          CaptchaHolder currentHolder = next;
          next = next.getNext();
          currentHolder.release();
        } while (next != null);
      }
    } else {
      CaptchaHolder next = this.firstHolder;
      do {
        CaptchaHolder currentHolder = next;
        next = next.getNext();
        currentHolder.release();
      } while (next != this.lastHolder);
    }
  }
}
