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
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.packet.ClientSettings;
import com.velocitypowered.proxy.protocol.packet.PluginMessage;
import com.velocitypowered.proxy.protocol.util.PluginMessageUtil;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.text.MessageFormat;
import java.util.List;
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

@SuppressFBWarnings("EI_EXPOSE_REP2")
public class BotFilterSessionHandler extends FallingCheckHandler {

  private static long TOTAL_TICKS;
  private static double CAPTCHA_Y;
  private static long TOTAL_TIME;

  private final Player player;
  private final LimboFilter plugin;
  private final Statistics statistics;
  private final Logger logger;
  private final CachedPackets packets;

  private final MinecraftPacket fallingCheckPos;
  private final MinecraftPacket fallingCheckChunk;
  private final MinecraftPacket fallingCheckView;

  private CheckState state = CheckState.valueOf(Settings.IMP.MAIN.CHECK_STATE);
  private LimboPlayer limboPlayer;
  private Limbo server;
  private String captchaAnswer;
  private int attempts = Settings.IMP.MAIN.CAPTCHA_ATTEMPTS;
  private int ignoredTicks = 0;
  private int nonValidPacketsSize = 0;
  private long joinTime = System.currentTimeMillis();
  private boolean startedListening = false;
  private boolean checkedBySettings = false;
  private boolean checkedByBrand = false;

  public BotFilterSessionHandler(Player player, LimboFilter plugin) {
    super(player.getProtocolVersion());

    this.player = player;
    this.plugin = plugin;

    this.statistics = this.plugin.getStatistics();
    this.logger = this.plugin.getLogger();
    this.packets = this.plugin.getPackets();

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
    this.limboPlayer = player;

    if (this.state == CheckState.ONLY_CAPTCHA) {
      this.sendCaptcha();
    } else if (this.state == CheckState.CAPTCHA_POSITION) {
      this.sendFallingCheckPackets();
      this.sendCaptcha();
    } else if (this.state == CheckState.ONLY_POSITION || this.state == CheckState.CAPTCHA_ON_POSITION_FAILED) {
      if (this.player.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_8) >= 0) {
        if (!Settings.IMP.MAIN.STRINGS.CHECKING_TITLE.isEmpty() && !Settings.IMP.MAIN.STRINGS.CHECKING_SUBTITLE.isEmpty()) {
          this.limboPlayer.writePacket(this.packets.getCheckingTitle());
        }
      }
      this.limboPlayer.writePacket(this.packets.getCheckingChat());
      this.sendFallingCheckPackets();
    }

    this.limboPlayer.flushPackets();
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
      if (this.lastY == CAPTCHA_Y || this.onGround) {
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
      if (this.ticks >= TOTAL_TICKS) {
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
        this.limboPlayer.writePacketAndFlush(expBuf);
      }

      ++this.ticks;
    }
  }

  @Override
  public void onChat(String message) {
    if ((this.state == CheckState.CAPTCHA_POSITION || this.state == CheckState.ONLY_CAPTCHA)) {
      if (message.equals(this.captchaAnswer)) {
        this.limboPlayer.writePacketAndFlush(this.packets.getResetSlot());
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
        this.logger.info("{} has client brand {}", this.player, brand);
        if (!Settings.IMP.MAIN.BLOCKED_CLIENT_BRANDS.contains(brand)) {
          this.checkedByBrand = true;
        }
      }
    } else if (packet instanceof ClientSettings) {
      if (!this.checkedBySettings && Settings.IMP.MAIN.CHECK_CLIENT_SETTINGS) {
        this.checkedBySettings = true;
      }
    }
  }

  private void finishCheck() {
    if (System.currentTimeMillis() - this.joinTime < TOTAL_TIME && this.state != CheckState.ONLY_CAPTCHA) {
      if (this.state == CheckState.CAPTCHA_POSITION && this.ticks < TOTAL_TICKS) {
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

    if (!this.checkedBySettings && Settings.IMP.MAIN.CHECK_CLIENT_SETTINGS) {
      this.disconnect(this.packets.getKickClientCheckSettings(), true);
    }
    if (!this.checkedByBrand && Settings.IMP.MAIN.CHECK_CLIENT_BRAND) {
      this.disconnect(this.packets.getKickClientCheckBrand(), true);
    }

    this.state = CheckState.SUCCESSFUL;
    this.plugin.cacheFilterUser(this.player);

    if (this.plugin.checkCpsLimit(Settings.IMP.MAIN.FILTER_AUTO_TOGGLE.ONLINE_MODE_VERIFY)
        || this.plugin.checkCpsLimit(Settings.IMP.MAIN.FILTER_AUTO_TOGGLE.NEED_TO_RECONNECT)) {
      this.disconnect(this.packets.getSuccessfulBotFilterDisconnect(), false);
    } else {
      this.limboPlayer.writePacketAndFlush(this.packets.getSuccessfulBotFilterChat());
      this.limboPlayer.disconnect();
    }
  }

  private void sendFallingCheckPackets() {
    this.limboPlayer.writePacket(this.fallingCheckPos);
    if (this.player.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_14) >= 0) {
      this.limboPlayer.writePacket(this.fallingCheckView);
    }

    this.limboPlayer.writePacket(this.fallingCheckChunk);
  }

  private void sendCaptcha() {
    ProtocolVersion version = this.player.getProtocolVersion();
    CaptchaHandler captchaHandler = this.plugin.getCachedCaptcha().randomCaptcha();
    String captchaAnswer = captchaHandler.getAnswer();
    this.setCaptchaAnswer(captchaAnswer);
    Settings.MAIN.STRINGS strings = Settings.IMP.MAIN.STRINGS;
    if (this.attempts == Settings.IMP.MAIN.CAPTCHA_ATTEMPTS) {
      this.limboPlayer.writePacket(
          this.packets.createChatPacket(this.plugin.getFactory(), MessageFormat.format(strings.CHECKING_CAPTCHA_CHAT, this.attempts))
      );
      if (version.compareTo(ProtocolVersion.MINECRAFT_1_8) >= 0) {
        if (!strings.CHECKING_CAPTCHA_TITLE.isEmpty() && !strings.CHECKING_CAPTCHA_SUBTITLE.isEmpty()) {
          this.limboPlayer.writePacket(
              this.packets.createTitlePacket(
                  this.plugin.getFactory(), strings.CHECKING_CAPTCHA_TITLE, MessageFormat.format(strings.CHECKING_CAPTCHA_SUBTITLE, this.attempts)
              )
          );
        }
      }
    } else {
      this.limboPlayer.writePacket(
          this.packets.createChatPacket(this.plugin.getFactory(), MessageFormat.format(strings.CHECKING_WRONG_CAPTCHA_CHAT, this.attempts))
      );
    }
    this.limboPlayer.writePacket(this.packets.getSetSlot());
    for (Object packet : captchaHandler.getMapPacket(version)) {
      this.limboPlayer.writePacket(packet);
    }

    this.limboPlayer.flushPackets();
  }

  private void fallingCheckFailed() {
    if (this.state == CheckState.CAPTCHA_ON_POSITION_FAILED) {
      List<PreparedPacket> expList = this.packets.getExperience();
      this.limboPlayer.writePacketAndFlush(expList.get(expList.size() - 1));
      this.changeStateToCaptcha();
      return;
    }

    this.disconnect(this.packets.getFallingCheckFailed(), true);
  }

  private void disconnect(PreparedPacket reason, boolean blocked) {
    this.limboPlayer.closeWith(reason);
    if (blocked) {
      this.statistics.addBlockedConnection();
    }
  }

  private boolean checkY() {
    return Math.abs(this.lastY - this.posY - getLoadedChunkSpeed(this.ticks)) > Settings.IMP.MAIN.MAX_VALID_POSITION_DIFFERENCE;
  }

  private void setCaptchaPositionAndDisableFalling() {
    this.server.respawnPlayer(this.player);
    this.limboPlayer.writePacketAndFlush(this.packets.getNoAbilities());

    this.waitingTeleportId = this.validTeleportId;
  }

  public void setCaptchaAnswer(String captchaAnswer) {
    this.captchaAnswer = captchaAnswer;
  }

  private void changeStateToCaptcha() {
    this.state = CheckState.ONLY_CAPTCHA;
    this.joinTime = System.currentTimeMillis() + TOTAL_TIME;
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

  public static void reload() {
    TOTAL_TICKS = Settings.IMP.MAIN.FALLING_CHECK_TICKS;
    TOTAL_TIME = (TOTAL_TICKS * 50) - 100;
    CAPTCHA_Y = Settings.IMP.MAIN.COORDS.CAPTCHA_Y;
  }

  public static long getTotalTicks() {
    return TOTAL_TICKS;
  }

  @SuppressWarnings("unused")
  public enum CheckState {

    ONLY_POSITION,
    ONLY_CAPTCHA,
    CAPTCHA_POSITION,
    CAPTCHA_ON_POSITION_FAILED,
    SUCCESSFUL,
    FAILED
  }
}
