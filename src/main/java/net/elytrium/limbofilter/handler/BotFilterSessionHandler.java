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

package net.elytrium.limbofilter.handler;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.packet.ClientSettings;
import com.velocitypowered.proxy.protocol.packet.PluginMessage;
import com.velocitypowered.proxy.protocol.util.PluginMessageUtil;
import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.chunk.VirtualChunk;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.elytrium.limboapi.api.protocol.PreparedPacket;
import net.elytrium.limboapi.api.protocol.packets.BuiltInPackets;
import net.elytrium.limbofilter.LimboFilter;
import net.elytrium.limbofilter.Settings;
import net.elytrium.limbofilter.cache.CachedPackets;
import net.elytrium.limbofilter.cache.captcha.CaptchaHandler;
import net.elytrium.limbofilter.stats.Statistics;
import org.slf4j.Logger;

public class BotFilterSessionHandler extends FallingCheckHandler {

  private static long FALLING_CHECK_TOTAL_TIME;

  private final Player proxyPlayer;
  private final LimboFilter plugin;
  private final Statistics statistics;
  private final Logger logger;
  private final CachedPackets packets;

  private final MinecraftPacket fallingCheckPos;
  private final MinecraftPacket fallingCheckChunk;
  private final MinecraftPacket fallingCheckView;

  private final long joinTime = System.currentTimeMillis();
  private ScheduledTask filterMainTask;

  private CheckState state;
  private LimboPlayer player;
  private Limbo server;
  private String captchaAnswer;
  private int attempts = Settings.IMP.MAIN.CAPTCHA_ATTEMPTS;
  private int ignoredTicks = 0;
  private int nonValidPacketsSize = 0;
  private boolean startedListening = false;
  private boolean checkedBySettings = false;
  private boolean checkedByBrand = false;

  public BotFilterSessionHandler(Player proxyPlayer, LimboFilter plugin) {
    super(proxyPlayer.getProtocolVersion());

    this.proxyPlayer = proxyPlayer;
    this.plugin = plugin;

    this.statistics = this.plugin.getStatistics();
    this.logger = this.plugin.getLogger();
    this.packets = this.plugin.getPackets();

    this.state = plugin.checkCpsLimit(Settings.IMP.MAIN.FILTER_AUTO_TOGGLE.CHECK_STATE_TOGGLE)
        ? CheckState.valueOf(Settings.IMP.MAIN.CHECK_STATE) : CheckState.valueOf(Settings.IMP.MAIN.CHECK_STATE_NON_TOGGLED);

    Settings.MAIN.COORDS coords = Settings.IMP.MAIN.COORDS;
    this.fallingCheckPos = this.createPlayerPosAndLook(
        this.plugin.getFactory(),
        this.validX, this.validY, this.validZ,
        (float) (this.state == CheckState.CAPTCHA_POSITION ? coords.CAPTCHA_YAW : coords.FALLING_CHECK_YAW),
        (float) (this.state == CheckState.CAPTCHA_POSITION ? coords.CAPTCHA_PITCH : coords.FALLING_CHECK_PITCH)
    );
    this.fallingCheckChunk = this.createChunkData(
        this.plugin.getFactory(), this.plugin.getFactory().createVirtualChunk(this.validX >> 4, this.validZ >> 4)
    );
    this.fallingCheckView = this.createUpdateViewPosition(this.plugin.getFactory(), this.validX, this.validZ);
  }

  @Override
  public void onSpawn(Limbo server, LimboPlayer player) {
    this.server = server;
    this.player = player;

    if (this.state == CheckState.ONLY_CAPTCHA) {
      this.sendCaptcha();
    } else if (this.state == CheckState.CAPTCHA_POSITION) {
      this.sendFallingCheckPackets();
      this.sendCaptcha();
    } else if (this.state == CheckState.ONLY_POSITION || this.state == CheckState.CAPTCHA_ON_POSITION_FAILED) {
      if (this.proxyPlayer.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_8) >= 0) {
        if (!Settings.IMP.MAIN.STRINGS.CHECKING_TITLE.isEmpty() && !Settings.IMP.MAIN.STRINGS.CHECKING_SUBTITLE.isEmpty()) {
          this.player.writePacket(this.packets.getCheckingTitle());
        }
      }
      this.player.writePacket(this.packets.getCheckingChat());
      this.sendFallingCheckPackets();
    }

    this.player.flushPackets();

    this.filterMainTask = this.plugin.getServer().getScheduler().buildTask(this.plugin, () -> {
      // TODO: Maybe check for max ping?
      if (System.currentTimeMillis() - BotFilterSessionHandler.this.joinTime > Settings.IMP.MAIN.TIME_OUT) {
        BotFilterSessionHandler.this.disconnect(BotFilterSessionHandler.this.packets.getTimesUp(), true);
      }
    }).delay(1, TimeUnit.SECONDS).repeat(1, TimeUnit.SECONDS).schedule();
  }

  @Override
  public void onMove() {
    if (!this.startedListening && this.state != CheckState.ONLY_CAPTCHA) {
      if (this.posX == this.validX && this.posZ == this.validZ) {
        this.startedListening = true;
      }
      if (this.nonValidPacketsSize > Settings.IMP.MAIN.NON_VALID_POSITION_XZ_ATTEMPTS) {
        this.fallingCheckFailed();
        return;
      }

      this.lastY = this.validY;
      ++this.nonValidPacketsSize;
    }
    if (this.startedListening && this.state != CheckState.SUCCESSFUL) {
      if (this.lastY == Settings.IMP.MAIN.COORDS.CAPTCHA_Y || this.onGround) {
        return;
      }
      if (this.state == CheckState.ONLY_CAPTCHA) {
        if (this.lastY != this.posY && this.waitingTeleportId == -1) {
          this.setCaptchaPositionAndDisableFalling();
        }
        return;
      }
      if (this.lastY - this.posY == 0) {
        ++this.ignoredTicks;
        return;
      }
      if (this.ticks >= Settings.IMP.MAIN.FALLING_CHECK_TICKS) {
        if (this.state == CheckState.CAPTCHA_POSITION) {
          this.changeStateToCaptcha();
        } else {
          this.finishCheck();
        }
        return;
      }
      if (Settings.IMP.MAIN.FALLING_CHECK_DEBUG) {
        System.out.println(
            "lastY=" + this.lastY + "; y=" + this.posY + "; diff=" + (this.lastY - this.posY)
                + "; need=" + getLoadedChunkSpeed(this.ticks) + "; ticks=" + this.ticks
                + "; x=" + this.posX + "; z=" + this.posZ + "; validX=" + this.validX + "; validZ=" + this.validZ
                + "; ignoredTicks=" + this.ignoredTicks + "; state=" + this.state
        );
      }
      if (this.ignoredTicks > Settings.IMP.MAIN.NON_VALID_POSITION_Y_ATTEMPTS) {
        this.fallingCheckFailed();
        return;
      }
      if ((this.posX != this.validX && this.posZ != this.validZ) || this.checkY()) {
        this.fallingCheckFailed();
        return;
      }
      PreparedPacket expBuf = this.packets.getExperience().get(this.ticks);
      if (expBuf != null) {
        this.player.writePacketAndFlush(expBuf);
      }

      ++this.ticks;
    }
  }

  @Override
  public void onChat(String message) {
    if ((this.state == CheckState.CAPTCHA_POSITION || this.state == CheckState.ONLY_CAPTCHA)) {
      if (message.equals(this.captchaAnswer)) {
        this.player.writePacketAndFlush(this.packets.getResetSlot());
        this.finishCheck();
      } else if (--this.attempts != 0) {
        this.sendCaptcha();
      } else {
        this.disconnect(this.packets.getCaptchaFailed(), true);
      }
    }
  }

  @Override
  public void onGeneric(Object packet) {
    if (packet instanceof PluginMessage) {
      PluginMessage pluginMessage = (PluginMessage) packet;
      if (PluginMessageUtil.isMcBrand(pluginMessage) && !this.checkedByBrand) {
        String brand = PluginMessageUtil.readBrandMessage(pluginMessage.content());
        this.logger.info("{} has client brand {}", this.proxyPlayer, brand);
        if (!Settings.IMP.MAIN.BLOCKED_CLIENT_BRANDS.contains(brand)) {
          this.checkedByBrand = true;
        }
      }
    } else if (packet instanceof ClientSettings) {
      if (Settings.IMP.MAIN.CHECK_CLIENT_SETTINGS && !this.checkedBySettings) {
        this.checkedBySettings = true;
      }
    }
  }

  @Override
  public void onDisconnect() {
    this.filterMainTask.cancel();
  }

  private void finishCheck() {
    if (System.currentTimeMillis() - this.joinTime < FALLING_CHECK_TOTAL_TIME && this.state != CheckState.ONLY_CAPTCHA) {
      if (this.state == CheckState.CAPTCHA_POSITION && this.ticks < Settings.IMP.MAIN.FALLING_CHECK_TICKS) {
        this.state = CheckState.ONLY_POSITION;
      } else {
        if (this.state == CheckState.CAPTCHA_ON_POSITION_FAILED) {
          this.changeStateToCaptcha();
        } else {
          this.disconnect(this.packets.getFallingCheckFailed(), true);
        }
      }
      return;
    }

    if (Settings.IMP.MAIN.CHECK_CLIENT_SETTINGS && !this.checkedBySettings) {
      this.disconnect(this.packets.getKickClientCheckSettings(), true);
    }
    if (Settings.IMP.MAIN.CHECK_CLIENT_BRAND && !this.checkedByBrand) {
      this.disconnect(this.packets.getKickClientCheckBrand(), true);
    }

    this.state = CheckState.SUCCESSFUL;
    this.plugin.cacheFilterUser(this.proxyPlayer);

    if (this.plugin.checkCpsLimit(Settings.IMP.MAIN.FILTER_AUTO_TOGGLE.ONLINE_MODE_VERIFY)
        || this.plugin.checkCpsLimit(Settings.IMP.MAIN.FILTER_AUTO_TOGGLE.NEED_TO_RECONNECT)) {
      this.disconnect(this.packets.getSuccessfulBotFilterDisconnect(), false);
    } else {
      this.player.writePacketAndFlush(this.packets.getSuccessfulBotFilterChat());
      this.player.disconnect();
    }
  }

  private void sendFallingCheckPackets() {
    this.player.writePacket(this.fallingCheckPos);
    if (this.proxyPlayer.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_14) >= 0) {
      this.player.writePacket(this.fallingCheckView);
    }

    this.player.writePacket(this.fallingCheckChunk);
  }

  private void sendCaptcha() {
    ProtocolVersion version = this.proxyPlayer.getProtocolVersion();
    CaptchaHandler captchaHandler = this.plugin.getCachedCaptcha().randomCaptcha();
    String captchaAnswer = captchaHandler.getAnswer();
    this.setCaptchaAnswer(captchaAnswer);
    Settings.MAIN.STRINGS strings = Settings.IMP.MAIN.STRINGS;
    if (this.attempts == Settings.IMP.MAIN.CAPTCHA_ATTEMPTS) {
      this.player.writePacket(
          this.packets.createChatPacket(this.plugin.getFactory(), MessageFormat.format(strings.CHECKING_CAPTCHA_CHAT, this.attempts))
      );
      if (version.compareTo(ProtocolVersion.MINECRAFT_1_8) >= 0) {
        if (!strings.CHECKING_CAPTCHA_TITLE.isEmpty() && !strings.CHECKING_CAPTCHA_SUBTITLE.isEmpty()) {
          this.player.writePacket(
              this.packets.createTitlePacket(
                  this.plugin.getFactory(),
                  MessageFormat.format(strings.CHECKING_CAPTCHA_TITLE, this.attempts),
                  MessageFormat.format(strings.CHECKING_CAPTCHA_SUBTITLE, this.attempts)
              )
          );
        }
      }
    } else {
      this.player.writePacket(
          this.packets.createChatPacket(this.plugin.getFactory(), MessageFormat.format(strings.CHECKING_WRONG_CAPTCHA_CHAT, this.attempts))
      );
    }
    this.player.writePacket(this.packets.getSetSlot());
    for (Object packet : captchaHandler.getMapPacket(version)) {
      this.player.writePacket(packet);
    }

    this.player.flushPackets();
  }

  private void fallingCheckFailed() {
    if (this.state == CheckState.CAPTCHA_ON_POSITION_FAILED) {
      List<PreparedPacket> expList = this.packets.getExperience();
      this.player.writePacketAndFlush(expList.get(expList.size() - 1));
      this.changeStateToCaptcha();
      return;
    }

    this.disconnect(this.packets.getFallingCheckFailed(), true);
  }

  private void disconnect(PreparedPacket reason, boolean blocked) {
    this.player.closeWith(reason);
    if (blocked) {
      this.statistics.addBlockedConnection();
    }
  }

  private boolean checkY() {
    return Math.abs(this.lastY - this.posY - getLoadedChunkSpeed(this.ticks)) > Settings.IMP.MAIN.MAX_VALID_POSITION_DIFFERENCE;
  }

  private void setCaptchaPositionAndDisableFalling() {
    this.server.respawnPlayer(this.proxyPlayer);
    this.player.writePacketAndFlush(this.packets.getNoAbilities());

    this.waitingTeleportId = this.validTeleportId;
  }

  public void setCaptchaAnswer(String captchaAnswer) {
    this.captchaAnswer = captchaAnswer;
  }

  private void changeStateToCaptcha() {
    this.state = CheckState.ONLY_CAPTCHA;
    //this.joinTime = System.currentTimeMillis() + this.fallingCheckTotalTime;
    this.setCaptchaPositionAndDisableFalling();
    if (this.captchaAnswer == null) {
      this.sendCaptcha();
    }
  }

  private MinecraftPacket createChunkData(LimboFactory factory, VirtualChunk chunk) {
    chunk.setSkyLight(chunk.getX() % 16, 256, chunk.getZ() % 16, (byte) 1);
    return (MinecraftPacket) factory.instantiatePacket(BuiltInPackets.ChunkData, chunk.getFullChunkSnapshot(), true);
  }

  private MinecraftPacket createPlayerPosAndLook(LimboFactory factory, double x, double y, double z, float yaw, float pitch) {
    return (MinecraftPacket) factory.instantiatePacket(BuiltInPackets.PlayerPositionAndLook, x, y, z, yaw, pitch, -133, false, true);
  }

  private MinecraftPacket createUpdateViewPosition(LimboFactory factory, int x, int z) {
    return (MinecraftPacket) factory.instantiatePacket(BuiltInPackets.UpdateViewPosition, x >> 4, z >> 4);
  }

  public static void setFallingCheckTotalTime(long time) {
    FALLING_CHECK_TOTAL_TIME = time;
  }

  public enum CheckState {

    ONLY_POSITION,
    ONLY_CAPTCHA,
    CAPTCHA_POSITION,
    CAPTCHA_ON_POSITION_FAILED,
    SUCCESSFUL
  }
}
