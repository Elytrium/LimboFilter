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

package net.elytrium.limbofilter.handler;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.protocol.packet.ClientSettingsPacket;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import com.velocitypowered.proxy.protocol.util.PluginMessageUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.elytrium.limboapi.api.protocol.PreparedPacket;
import net.elytrium.limbofilter.LimboFilter;
import net.elytrium.limbofilter.Settings;
import net.elytrium.limbofilter.captcha.CaptchaHolder;
import net.elytrium.limbofilter.listener.TcpListener;
import net.elytrium.limbofilter.protocol.data.EntityMetadata;
import net.elytrium.limbofilter.protocol.data.ItemFrame;
import net.elytrium.limbofilter.protocol.packets.Interact;
import net.elytrium.limbofilter.protocol.packets.SetEntityMetadata;
import net.elytrium.limbofilter.stats.Statistics;

public class BotFilterSessionHandler implements LimboSessionHandler {

  private static final double[] LOADED_CHUNK_SPEED_CACHE = new double[Settings.IMP.MAIN.FALLING_CHECK_TICKS];
  private static long FALLING_CHECK_TOTAL_TIME;

  private final Map<Integer, Integer> frameRotation = new HashMap<>();
  private final Player proxyPlayer;
  private final ProtocolVersion version;
  private final LimboFilter plugin;
  private final Statistics statistics;
  private final int validX;
  private final int validY;
  private final int validZ;
  private final int validTeleportId;

  private double posX;
  private double posY;
  private double lastY;
  private double posZ;
  private int waitingTeleportId;
  private boolean onGround;

  private int ticks = 1;
  private int ignoredTicks;

  private long joinTime;
  private ScheduledFuture<?> filterMainTask;

  private CheckState state;
  private LimboPlayer player;
  private Limbo server;
  private String captchaAnswer;
  private int attempts = Settings.IMP.MAIN.CAPTCHA_ATTEMPTS;
  private int nonValidPacketsSize;
  private boolean startedListening;
  private boolean checkedBySettings;
  private boolean checkedByBrand;

  public BotFilterSessionHandler(Player proxyPlayer, LimboFilter plugin) {
    this.proxyPlayer = proxyPlayer;
    this.version = this.proxyPlayer.getProtocolVersion();
    this.plugin = plugin;

    this.statistics = this.plugin.getStatistics();

    Settings.MAIN.FALLING_COORDS fallingCoords = Settings.IMP.MAIN.FALLING_COORDS;
    this.validX = fallingCoords.X;
    this.validY = fallingCoords.Y;
    this.validZ = fallingCoords.Z;
    this.validTeleportId = fallingCoords.TELEPORT_ID;

    this.posX = this.validX;
    this.posY = this.validY;
    this.posZ = this.validZ;

    if (proxyPlayer.getRemoteAddress().getPort() == 0) {
      this.state = plugin.checkCpsLimit(Settings.IMP.MAIN.FILTER_AUTO_TOGGLE.CHECK_STATE_TOGGLE)
          ? Settings.IMP.MAIN.GEYSER_CHECK_STATE : Settings.IMP.MAIN.GEYSER_CHECK_STATE_NON_TOGGLED;
    } else {
      this.state = plugin.checkCpsLimit(Settings.IMP.MAIN.FILTER_AUTO_TOGGLE.CHECK_STATE_TOGGLE)
          ? Settings.IMP.MAIN.CHECK_STATE : Settings.IMP.MAIN.CHECK_STATE_NON_TOGGLED;
    }
  }

  @Override
  public void onSpawn(Limbo server, LimboPlayer player) {
    this.server = server;
    this.player = player;

    this.joinTime = System.currentTimeMillis();
    if (this.state == CheckState.ONLY_CAPTCHA) {
      this.changeStateToCaptcha();
    } else if (this.state == CheckState.ONLY_POSITION || this.state == CheckState.CAPTCHA_ON_POSITION_FAILED) {
      this.sendFallingCheckPackets();
      this.sendFallingCheckTitleAndChat();
    } else if (this.state == CheckState.CAPTCHA_POSITION) {
      this.sendFallingCheckPackets();

      if (Settings.IMP.MAIN.FRAMED_CAPTCHA.FRAMED_CAPTCHA_ENABLED) {
        this.sendFallingCheckTitleAndChat();
      }
    }

    this.player.flushPackets();

    this.filterMainTask = player.getScheduledExecutor().schedule(() ->
        this.disconnect(this.plugin.getPackets().getTimesUp(), true), this.getTimeout(), TimeUnit.MILLISECONDS);
  }

  private void sendFallingCheckPackets() {
    this.player.writePacket(this.plugin.getPackets().getFallingCheckPackets());
  }

  private void sendFallingCheckTitleAndChat() {
    this.player.writePacket(this.plugin.getPackets().getFallingCheckTitleAndChat());
  }

  @Override
  public void onMove(double x, double y, double z) {
    if (this.version.compareTo(ProtocolVersion.MINECRAFT_1_8) <= 0
        && x == this.validX && y == this.validY && z == this.validZ && this.waitingTeleportId == this.validTeleportId) {
      this.ticks = 1;
      this.posY = -1;
      this.waitingTeleportId = -1;
    }

    this.posX = x;
    this.lastY = this.posY;
    this.posY = y;
    this.posZ = z;

    if (Settings.IMP.MAIN.FALLING_CHECK_DEBUG) {
      this.logPosition();
    }
    if (!this.startedListening && this.state != CheckState.ONLY_CAPTCHA) {
      if (this.posX == this.validX && this.posZ == this.validZ) {
        this.startedListening = true;

        if (this.state == CheckState.CAPTCHA_POSITION && !Settings.IMP.MAIN.FRAMED_CAPTCHA.FRAMED_CAPTCHA_ENABLED) {
          this.sendCaptcha();
        }
      }
      if (this.nonValidPacketsSize > Settings.IMP.MAIN.NON_VALID_POSITION_XZ_ATTEMPTS) {
        this.fallingCheckFailed("A lot of non-valid XZ attempts");
        return;
      }

      this.lastY = this.validY;
      ++this.nonValidPacketsSize;
    }
    if (this.startedListening && this.state != CheckState.SUCCESSFUL && this.state != CheckState.ONLY_CAPTCHA && !this.onGround) {
      if (this.lastY - this.posY == 0) {
        ++this.ignoredTicks;
        return;
      }
      if (this.ignoredTicks > Settings.IMP.MAIN.NON_VALID_POSITION_Y_ATTEMPTS) {
        this.fallingCheckFailed("A lot of non-valid Y attempts");
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
      if (this.checkY()) {
        this.fallingCheckFailed("Non-valid X, Z or Velocity");
        return;
      }
      PreparedPacket expBuf = this.plugin.getPackets().getExperience(this.ticks);
      if (expBuf != null) {
        this.player.writePacketAndFlush(expBuf);
      }

      ++this.ticks;
    }
  }

  private void fallingCheckFailed(String reason) {
    if (Settings.IMP.MAIN.FALLING_CHECK_DEBUG) {
      LimboFilter.getLogger().info(reason);
      this.logPosition();
    }

    if (this.state == CheckState.CAPTCHA_ON_POSITION_FAILED) {
      this.player.writePacketAndFlush(this.plugin.getPackets().getLastExperience());
      this.changeStateToCaptcha();
    } else {
      this.disconnect(this.plugin.getPackets().getFallingCheckFailed(), true);
    }
  }

  private void logPosition() {
    LimboFilter.getLogger().info(
        "lastY=" + this.lastY + "; y=" + this.posY + "; delta=" + (this.lastY - this.posY) + "; need=" + getLoadedChunkSpeed(this.ticks)
            + "; x=" + this.posX + "; z=" + this.posZ + "; validX=" + this.validX + "; validY=" + this.validY + "; validZ=" + this.validZ
            + "; ticks=" + this.ticks + "; ignoredTicks=" + this.ignoredTicks + "; state=" + this.state
            + "; diff=" + (this.lastY - this.posY - getLoadedChunkSpeed(this.ticks))
    );
  }

  private boolean checkY() {
    while (this.ticks < LOADED_CHUNK_SPEED_CACHE.length
        && Math.abs(this.lastY - this.posY - getLoadedChunkSpeed(this.ticks)) > Settings.IMP.MAIN.MAX_VALID_POSITION_DIFFERENCE) {
      ++this.ticks;
      ++this.ignoredTicks;
    }

    return this.ticks >= LOADED_CHUNK_SPEED_CACHE.length;
  }

  @Override
  public void onGround(boolean onGround) {
    this.onGround = onGround;
  }

  @Override
  public void onTeleport(int teleportId) {
    if (teleportId == this.waitingTeleportId) {
      this.ticks = 1;
      this.posY = -1;
      this.lastY = -1;
      this.waitingTeleportId = -1;
    }
  }

  @Override
  public void onChat(String message) {
    if (this.state == CheckState.CAPTCHA_POSITION || this.state == CheckState.ONLY_CAPTCHA) {
      if (this.equalsCaptchaAnswer(message) || (message.startsWith("/") && this.equalsCaptchaAnswer(message.substring(1)))) {
        this.player.writePacketAndFlush(this.plugin.getPackets().getResetSlot());
        this.finishCheck();
      } else if (--this.attempts != 0) {
        this.sendCaptcha();
      } else {
        this.disconnect(this.plugin.getPackets().getCaptchaFailed(), true);
      }
    }
  }

  private boolean equalsCaptchaAnswer(String message) {
    if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.IGNORE_CASE) {
      return message.equalsIgnoreCase(this.captchaAnswer);
    } else {
      return message.equals(this.captchaAnswer);
    }
  }

  @Override
  public void onGeneric(Object packet) {
    if (packet instanceof PluginMessagePacket) {
      PluginMessagePacket pluginMessage = (PluginMessagePacket) packet;
      if (PluginMessageUtil.isMcBrand(pluginMessage) && !this.checkedByBrand) {
        String brand = PluginMessageUtil.readBrandMessage(pluginMessage.content());
        LimboFilter.getLogger().info("{} has client brand {}", this.proxyPlayer, brand);
        if (!Settings.IMP.MAIN.BLOCKED_CLIENT_BRANDS.contains(brand)) {
          this.checkedByBrand = true;
        }
      }
    } else if (packet instanceof ClientSettingsPacket) {
      if (Settings.IMP.MAIN.CHECK_CLIENT_SETTINGS && !this.checkedBySettings) {
        this.checkedBySettings = true;
      }
    } else if (packet instanceof Interact) {
      Interact interact = (Interact) packet;
      if (interact.getType() == 0 || interact.getType() == 1) {
        int rotation = this.frameRotation.compute(interact.getEntityId(), (k, v) -> (v != null ? v : 0) + 1);
        EntityMetadata metadata = ItemFrame.createRotationMetadata(this.version, rotation);
        this.player.writePacketAndFlush(new SetEntityMetadata(interact.getEntityId(), metadata));
      }
    }
  }

  @Override
  public void onDisconnect() {
    this.filterMainTask.cancel(true);

    TcpListener tcpListener = this.plugin.getTcpListener();
    if (tcpListener != null) {
      tcpListener.removeAddress(this.proxyPlayer.getRemoteAddress().getAddress());
    }
  }

  private void finishCheck() {
    if (System.currentTimeMillis() - this.joinTime < FALLING_CHECK_TOTAL_TIME && this.state != CheckState.ONLY_CAPTCHA) {
      if (this.state == CheckState.CAPTCHA_POSITION && this.ticks < Settings.IMP.MAIN.FALLING_CHECK_TICKS) {
        this.state = CheckState.ONLY_POSITION;
      } else {
        if (this.state == CheckState.CAPTCHA_ON_POSITION_FAILED) {
          this.changeStateToCaptcha();
        } else {
          this.disconnect(this.plugin.getPackets().getFallingCheckFailed(), true);
        }
      }
      return;
    }

    if (Settings.IMP.MAIN.CHECK_CLIENT_SETTINGS && !this.checkedBySettings) {
      this.disconnect(this.plugin.getPackets().getKickClientCheckSettings(), true);
      return;
    }

    if (Settings.IMP.MAIN.CHECK_CLIENT_BRAND && !this.checkedByBrand) {
      this.disconnect(this.plugin.getPackets().getKickClientCheckBrand(), true);
      return;
    }

    if (this.checkPing()) {
      return;
    }

    this.state = CheckState.SUCCESSFUL;
    this.plugin.cacheFilterUser(this.proxyPlayer);

    if (this.plugin.checkCpsLimit(Settings.IMP.MAIN.FILTER_AUTO_TOGGLE.NEED_TO_RECONNECT)) {
      this.disconnect(this.plugin.getPackets().getSuccessfulBotFilterDisconnect(), false);
    } else {
      this.player.writePacketAndFlush(this.plugin.getPackets().getSuccessfulBotFilterChat());
      this.player.disconnect();
    }
  }

  private boolean checkPing() {
    int l7Ping = this.player.getPing();
    int l4Ping = this.statistics.getPing(this.proxyPlayer.getRemoteAddress().getAddress());

    if (Settings.IMP.MAIN.TCP_LISTENER.PROXY_DETECTOR_ENABLED && (l7Ping - l4Ping) > Settings.IMP.MAIN.TCP_LISTENER.PROXY_DETECTOR_DIFFERENCE) {
      this.disconnect(this.plugin.getPackets().getKickProxyCheck(), true);

      if (Settings.IMP.MAIN.TCP_LISTENER.DEBUG_ON_FAIL) {
        LimboFilter.getLogger().info("{} failed proxy check: L4 ping {}, L7 ping {}", this.proxyPlayer, l4Ping, l7Ping);
      }

      return true;
    }

    if (Settings.IMP.MAIN.TCP_LISTENER.DEBUG_ON_SUCCESS) {
      LimboFilter.getLogger().info("{} passed proxy check: L4 ping {}, L7 ping {}", this.proxyPlayer, l4Ping, l7Ping);
    }

    return false;
  }

  private void changeStateToCaptcha() {
    this.state = CheckState.ONLY_CAPTCHA;
    this.server.respawnPlayer(this.proxyPlayer);
    this.player.writePacketAndFlush(this.plugin.getPackets().getNoAbilities());

    this.waitingTeleportId = this.validTeleportId;
    if (this.captchaAnswer == null) {
      this.sendCaptcha();
    }
  }

  private void sendCaptcha() {
    CaptchaHolder captchaHolder = this.plugin.getNextCaptcha();

    if (captchaHolder == null) {
      this.player.closeWith(this.plugin.getPackets().getCaptchaNotReadyYet());
      return;
    }

    this.captchaAnswer = captchaHolder.getAnswer();

    PreparedPacket framedCaptchaPacket = this.plugin.getPackets().getFramedCaptchaPackets();
    if (framedCaptchaPacket != null) {
      this.player.writePacket(framedCaptchaPacket);
    }

    this.player.writePacket(this.plugin.getPackets().getCaptchaAttemptsPacket(this.attempts));
    for (Object packet : captchaHolder.getMapPacket(this.version)) {
      this.player.writePacket(packet);
    }

    this.player.flushPackets();
  }

  private void disconnect(PreparedPacket reason, boolean blocked) {
    this.player.closeWith(reason);
    if (blocked) {
      this.statistics.addBlockedConnection();
    }
  }

  private int getTimeout() {
    if (this.proxyPlayer.getRemoteAddress().getPort() == 0) {
      return Settings.IMP.MAIN.GEYSER_TIME_OUT;
    } else {
      return Settings.IMP.MAIN.TIME_OUT;
    }
  }

  static {
    for (int i = 0; i < Settings.IMP.MAIN.FALLING_CHECK_TICKS; ++i) {
      LOADED_CHUNK_SPEED_CACHE[i] = -((Math.pow(0.98, i) - 1) * 3.92);
    }
  }

  public static double getLoadedChunkSpeed(int ticks) {
    if (ticks == -1) {
      return 0;
    }

    return LOADED_CHUNK_SPEED_CACHE[ticks];
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
