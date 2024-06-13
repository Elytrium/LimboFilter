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

package net.elytrium.limbofilter;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.proxy.console.VelocityConsole;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOError;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.commons.kyori.serialization.Serializers;
import net.elytrium.commons.utils.updates.UpdatesChecker;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.chunk.VirtualWorld;
import net.elytrium.limboapi.api.file.WorldFile;
import net.elytrium.limboapi.api.protocol.PacketDirection;
import net.elytrium.limboapi.api.protocol.packets.PacketFactory;
import net.elytrium.limboapi.api.protocol.packets.PacketMapping;
import net.elytrium.limboapi.api.protocol.packets.data.MapData;
import net.elytrium.limboapi.api.protocol.packets.data.MapPalette;
import net.elytrium.limbofilter.cache.CachedPackets;
import net.elytrium.limbofilter.captcha.CaptchaGenerator;
import net.elytrium.limbofilter.captcha.CaptchaHolder;
import net.elytrium.limbofilter.commands.LimboFilterCommand;
import net.elytrium.limbofilter.commands.SendFilterCommand;
import net.elytrium.limbofilter.handler.BotFilterSessionHandler;
import net.elytrium.limbofilter.listener.FilterListener;
import net.elytrium.limbofilter.listener.TcpListener;
import net.elytrium.limbofilter.protocol.packets.Interact;
import net.elytrium.limbofilter.protocol.packets.SetEntityMetadata;
import net.elytrium.limbofilter.protocol.packets.SpawnEntity;
import net.elytrium.limbofilter.stats.Statistics;
import net.elytrium.pcap.PcapException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ComponentSerializer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bstats.velocity.Metrics;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.slf4j.Logger;

@Plugin(
    id = "limbofilter",
    name = "LimboFilter",
    version = BuildConstants.FILTER_VERSION,
    url = "https://elytrium.net/",
    authors = {
        "Elytrium (https://elytrium.net/)",
    },
    dependencies = {
        @Dependency(id = "limboapi")
    }
)
public class LimboFilter {

  @MonotonicNonNull
  private static Logger LOGGER;
  @MonotonicNonNull
  private static Serializer SERIALIZER;

  private final Map<String, CachedUser> cachedFilterChecks = new ConcurrentHashMap<>();

  private final Path dataDirectory;
  private final File configFile;
  private final Metrics.Factory metricsFactory;
  private final ProxyServer server;
  private final Statistics statistics;
  private final LimboFactory limboFactory;
  private final PacketFactory packetFactory;
  private final Level initialLogLevel;

  private Limbo filterServer;
  private VirtualWorld filterWorld;
  private ScheduledTask refreshCaptchaTask;
  private ScheduledTask purgeCacheTask;
  private ScheduledTask logEnablerTask;
  private CaptchaGenerator generator;
  private CachedPackets packets;
  private boolean logsDisabled;
  private TcpListener tcpListener;

  @Inject
  public LimboFilter(Logger logger, ProxyServer server, Metrics.Factory metricsFactory, @DataDirectory Path dataDirectory) {
    setLogger(logger);

    this.server = server;
    this.metricsFactory = metricsFactory;
    this.dataDirectory = dataDirectory;
    this.configFile = this.dataDirectory.resolve("config.yml").toFile();
    this.statistics = new Statistics();

    this.limboFactory = (LimboFactory) this.server.getPluginManager().getPlugin("limboapi").flatMap(PluginContainer::getInstance).orElseThrow();
    this.packetFactory = this.limboFactory.getPacketFactory();
    this.initialLogLevel = LogManager.getRootLogger().getLevel();
  }

  @Subscribe
  public void onProxyInitialization(ProxyInitializeEvent event) {
    Settings.IMP.setLogger(LOGGER);

    this.reload();

    Metrics metrics = this.metricsFactory.make(this, 13699);
    Settings.MAIN main = Settings.IMP.MAIN;
    metrics.addCustomChart(new SimplePie("filter_type", () -> String.valueOf(main.CHECK_STATE)));
    metrics.addCustomChart(new SimplePie("load_world", () -> String.valueOf(main.LOAD_WORLD)));
    metrics.addCustomChart(new SimplePie("check_brand", () -> String.valueOf(main.CHECK_CLIENT_BRAND)));
    metrics.addCustomChart(new SimplePie("check_settings", () -> String.valueOf(main.CHECK_CLIENT_SETTINGS)));
    metrics.addCustomChart(
        new SimplePie("has_backplate",
            () -> String.valueOf(!main.CAPTCHA_GENERATOR.BACKPLATE_PATHS.isEmpty() && !main.CAPTCHA_GENERATOR.BACKPLATE_PATHS.get(0).isEmpty()))
    );
    metrics.addCustomChart(new SingleLineChart("pings", () -> Math.toIntExact(this.statistics.getPings()))); // Total pings
    metrics.addCustomChart(new SingleLineChart("connections", () -> Math.toIntExact(this.statistics.getConnections())));

    if (!UpdatesChecker.checkVersionByURL("https://raw.githubusercontent.com/Elytrium/LimboFilter/master/VERSION", Settings.IMP.VERSION)) {
      LOGGER.error("****************************************");
      LOGGER.warn("The new LimboFilter update was found, please update.");
      LOGGER.error("https://github.com/Elytrium/LimboFilter/releases/");
      LOGGER.error("****************************************");
    }

    // Not letting VelocityConsole to inherit root logger since we can disable it
    org.apache.logging.log4j.Logger consoleLogger = LogManager.getLogger(VelocityConsole.class);
    Configurator.setLevel(consoleLogger.getName(), consoleLogger.getLevel());
  }

  @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH", justification = "LEGACY_AMPERSAND can't be null in velocity.")
  public void reload() {
    Settings.IMP.reload(this.configFile, Settings.IMP.PREFIX);

    ComponentSerializer<Component, Component, String> serializer = Settings.IMP.SERIALIZER.getSerializer();
    if (serializer == null) {
      LOGGER.warn("The specified serializer could not be founded, using default. (LEGACY_AMPERSAND)");
      setSerializer(new Serializer(Objects.requireNonNull(Serializers.LEGACY_AMPERSAND.getSerializer())));
    } else {
      setSerializer(new Serializer(serializer));
    }

    long captchaGeneratorRamConsumed = (long) MapData.MAP_SIZE * Settings.IMP.MAIN.CAPTCHA_GENERATOR.IMAGES_COUNT;

    if (Settings.IMP.MAIN.FRAMED_CAPTCHA.FRAMED_CAPTCHA_ENABLED) {
      captchaGeneratorRamConsumed *= (long) Settings.IMP.MAIN.FRAMED_CAPTCHA.WIDTH * Settings.IMP.MAIN.FRAMED_CAPTCHA.HEIGHT;
    }

    if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.PREPARE_CAPTCHA_PACKETS) {
      captchaGeneratorRamConsumed *= ProtocolVersion.values().length / 2f;
    } else {
      captchaGeneratorRamConsumed *= MapPalette.MapVersion.values().length;
    }

    double captchaGeneratorRamGigabytesConsumed = captchaGeneratorRamConsumed / 1024.0 / 1024.0 / 1024.0;

    String ramWarning = String.format("Current captcha generator settings will consume %.2fGB RAM normally and %.2fGB RAM on reloads",
        captchaGeneratorRamGigabytesConsumed, captchaGeneratorRamGigabytesConsumed * 2);

    if (captchaGeneratorRamConsumed > Runtime.getRuntime().maxMemory() * 2 / 3) {
      LOGGER.warn(ramWarning);
      LOGGER.warn("Modify the config to decrease RAM consumption");
    } else {
      LOGGER.info(ramWarning);
      LOGGER.info("Modify the config to decrease RAM consumption");
    }

    BotFilterSessionHandler.setFallingCheckTotalTime(Settings.IMP.MAIN.FALLING_CHECK_TICKS * 50L); // One tick == 50 millis

    this.statistics.restartUpdateTasks(this, this.server.getScheduler());

    if (this.refreshCaptchaTask != null) {
      this.refreshCaptchaTask.cancel();
    }

    if (this.generator != null) {
      this.generator.shutdown();
    }

    this.generator = new CaptchaGenerator(this);
    this.generator.initializeGenerator();

    this.refreshCaptchaTask = this.server.getScheduler()
        .buildTask(this, this.generator::generateImages)
        .repeat(Settings.IMP.MAIN.CAPTCHA_REGENERATE_RATE, TimeUnit.SECONDS)
        .schedule();

    this.cachedFilterChecks.clear();

    Settings.IMP.MAIN.WHITELISTED_PLAYERS.forEach(player -> {
      try {
        this.cachedFilterChecks.put(player.USERNAME, new CachedUser(InetAddress.getByName(player.IP), Long.MAX_VALUE));
      } catch (UnknownHostException e) {
        throw new IllegalArgumentException(e);
      }
    });

    Settings.MAIN.COORDS captchaCoords = Settings.IMP.MAIN.COORDS;
    this.filterWorld = this.limboFactory.createVirtualWorld(
        Settings.IMP.MAIN.BOTFILTER_DIMENSION,
        captchaCoords.CAPTCHA_X, captchaCoords.CAPTCHA_Y, captchaCoords.CAPTCHA_Z,
        (float) captchaCoords.CAPTCHA_YAW, (float) captchaCoords.CAPTCHA_PITCH
    );

    // Make LimboAPI preload parent to captcha chunks to ensure that Sodium can properly render captcha.
    if (Settings.IMP.MAIN.FRAMED_CAPTCHA.FRAMED_CAPTCHA_ENABLED) {
      Settings.MAIN.FRAMED_CAPTCHA settings = Settings.IMP.MAIN.FRAMED_CAPTCHA;
      for (int x = 0; x < settings.WIDTH; x++) {
        this.filterWorld.getChunkOrNew(settings.COORDS.X + x, settings.COORDS.Z);
      }

      for (int x = -1; x <= 1; x++) {
        for (int z = -1; z <= 1; z++) {
          this.filterWorld.getChunkOrNew(
              (int) captchaCoords.CAPTCHA_X + (x * 16),
              (int) captchaCoords.CAPTCHA_Z + (z * 16)
          );
        }
      }
    }

    if (Settings.IMP.MAIN.LOAD_WORLD) {
      try {
        Path path = this.dataDirectory.resolve(Settings.IMP.MAIN.WORLD_FILE_PATH);
        WorldFile file = this.limboFactory.openWorldFile(Settings.IMP.MAIN.WORLD_FILE_TYPE, path);

        Settings.MAIN.WORLD_COORDS coords = Settings.IMP.MAIN.WORLD_COORDS;
        file.toWorld(this.limboFactory, this.filterWorld, coords.X, coords.Y, coords.Z, Settings.IMP.MAIN.WORLD_LIGHT_LEVEL);
      } catch (IOException e) {
        throw new IllegalArgumentException(e);
      }
    }

    if (Settings.IMP.MAIN.WORLD_OVERRIDE_BLOCK_LIGHT_LEVEL) {
      this.filterWorld.fillBlockLight(Settings.IMP.MAIN.WORLD_LIGHT_LEVEL);
    }

    if (this.filterServer != null) {
      this.filterServer.dispose();
    }

    this.filterServer = this.limboFactory.createLimbo(this.filterWorld)
        .setName("LimboFilter")
        .setReadTimeout(Settings.IMP.MAIN.MAX_PING)
        .setWorldTime(Settings.IMP.MAIN.WORLD_TICKS)
        .setGameMode(Settings.IMP.MAIN.GAME_MODE)
        .setShouldRespawn(false)
        .setShouldUpdateTags(false)
        .registerPacket(PacketDirection.SERVERBOUND, Interact.class, Interact::new, new PacketMapping[]{
            new PacketMapping(0x02, ProtocolVersion.MINIMUM_VERSION, false),
            new PacketMapping(0x0A, ProtocolVersion.MINECRAFT_1_9, false),
            new PacketMapping(0x0B, ProtocolVersion.MINECRAFT_1_12, false),
            new PacketMapping(0x0A, ProtocolVersion.MINECRAFT_1_12_1, false),
            new PacketMapping(0x0D, ProtocolVersion.MINECRAFT_1_13, false),
            new PacketMapping(0x0E, ProtocolVersion.MINECRAFT_1_14, false),
            new PacketMapping(0x0D, ProtocolVersion.MINECRAFT_1_17, false),
            new PacketMapping(0x0F, ProtocolVersion.MINECRAFT_1_19, false),
            new PacketMapping(0x10, ProtocolVersion.MINECRAFT_1_19_1, false),
            new PacketMapping(0x0F, ProtocolVersion.MINECRAFT_1_19_3, false),
            new PacketMapping(0x10, ProtocolVersion.MINECRAFT_1_19_4, false),
            new PacketMapping(0x12, ProtocolVersion.MINECRAFT_1_20_2, false),
            new PacketMapping(0x13, ProtocolVersion.MINECRAFT_1_20_3, false),
            new PacketMapping(0x16, ProtocolVersion.MINECRAFT_1_20_5, false),
        })
        .registerPacket(PacketDirection.CLIENTBOUND, SetEntityMetadata.class, SetEntityMetadata::new, new PacketMapping[]{
            new PacketMapping(0x1C, ProtocolVersion.MINIMUM_VERSION, true),
            new PacketMapping(0x39, ProtocolVersion.MINECRAFT_1_9, true),
            new PacketMapping(0x3B, ProtocolVersion.MINECRAFT_1_12, true),
            new PacketMapping(0x3C, ProtocolVersion.MINECRAFT_1_12_1, true),
            new PacketMapping(0x3F, ProtocolVersion.MINECRAFT_1_13, true),
            new PacketMapping(0x43, ProtocolVersion.MINECRAFT_1_14, true),
            new PacketMapping(0x44, ProtocolVersion.MINECRAFT_1_15, true),
            new PacketMapping(0x4D, ProtocolVersion.MINECRAFT_1_17, true),
            new PacketMapping(0x50, ProtocolVersion.MINECRAFT_1_19_1, true),
            new PacketMapping(0x4E, ProtocolVersion.MINECRAFT_1_19_3, true),
            new PacketMapping(0x52, ProtocolVersion.MINECRAFT_1_19_4, true),
            new PacketMapping(0x54, ProtocolVersion.MINECRAFT_1_20_2, true),
            new PacketMapping(0x56, ProtocolVersion.MINECRAFT_1_20_3, true),
            new PacketMapping(0x58, ProtocolVersion.MINECRAFT_1_20_5, true),
        })
        .registerPacket(PacketDirection.CLIENTBOUND, SpawnEntity.class, SpawnEntity::new, new PacketMapping[]{
            new PacketMapping(0x0E, ProtocolVersion.MINIMUM_VERSION, true),
            new PacketMapping(0x00, ProtocolVersion.MINECRAFT_1_9, true),
            new PacketMapping(0x01, ProtocolVersion.MINECRAFT_1_19_4, true),
        });

    CachedPackets cachedPackets = new CachedPackets();
    cachedPackets.createPackets(this.limboFactory, this.packetFactory);

    CachedPackets previousCachedPackets = this.packets;
    this.packets = cachedPackets;

    if (previousCachedPackets != null) {
      previousCachedPackets.dispose();
    }

    CommandManager manager = this.server.getCommandManager();
    manager.unregister("limbofilter");
    manager.unregister("sendfilter");

    manager.register("limbofilter", new LimboFilterCommand(this), "lf", "botfilter", "bf", "lfilter");
    manager.register("sendfilter", new SendFilterCommand(this));

    this.server.getEventManager().unregisterListeners(this);
    this.server.getEventManager().register(this, new FilterListener(this));

    if (this.tcpListener != null) {
      this.tcpListener.stop();
      this.tcpListener = null;
    }

    if (Settings.IMP.MAIN.TCP_LISTENER.PROXY_DETECTOR_ENABLED) {
      try {
        LOGGER.info("Initializing TCP Listener");
        this.tcpListener = new TcpListener(this);
        this.tcpListener.start();
      } catch (PcapException e) {
        new Exception("Got exception when starting TCP listener. Disable it if you are unsure what does it does.", e).printStackTrace();
      }
    }

    if (this.purgeCacheTask != null) {
      this.purgeCacheTask.cancel();
    }

    this.purgeCacheTask = this.server.getScheduler()
        .buildTask(this, () -> this.checkCache(this.cachedFilterChecks))
        .delay(Settings.IMP.MAIN.PURGE_CACHE_MILLIS, TimeUnit.MILLISECONDS)
        .repeat(Settings.IMP.MAIN.PURGE_CACHE_MILLIS, TimeUnit.MILLISECONDS)
        .schedule();

    if (this.logEnablerTask != null) {
      this.logEnablerTask.cancel();
    }

    this.logEnablerTask = this.server.getScheduler()
        .buildTask(this, this::checkLoggerToEnable)
        .delay(Settings.IMP.MAIN.LOG_ENABLER_CHECK_REFRESH_RATE, TimeUnit.MILLISECONDS)
        .repeat(Settings.IMP.MAIN.LOG_ENABLER_CHECK_REFRESH_RATE, TimeUnit.MILLISECONDS)
        .schedule();
  }

  public void cacheFilterUser(Player player) {
    String username = player.getUsername();
    this.cachedFilterChecks.remove(username);
    this.cachedFilterChecks.put(
        username,
        new CachedUser(player.getRemoteAddress().getAddress(), System.currentTimeMillis() + Settings.IMP.MAIN.PURGE_CACHE_MILLIS)
    );
  }

  public void resetCacheForFilterUser(Player player) {
    this.cachedFilterChecks.remove(player.getUsername());
  }

  public boolean shouldCheck(Player player) {
    if (!this.checkCpsLimit(Settings.IMP.MAIN.FILTER_AUTO_TOGGLE.ALL_BYPASS)) {
      return false;
    }

    if (player.isOnlineMode() && !this.checkCpsLimit(Settings.IMP.MAIN.FILTER_AUTO_TOGGLE.ONLINE_MODE_BYPASS)) {
      return false;
    }

    return this.shouldCheck(player.getUsername(), player.getRemoteAddress().getAddress());
  }

  public boolean shouldCheck(String nickname, InetAddress ip) {
    if (this.cachedFilterChecks.containsKey(nickname)) {
      return !ip.equals(this.cachedFilterChecks.get(nickname).getInetAddress());
    } else {
      return true;
    }
  }

  public void sendToFilterServer(Player player) {
    try {
      if (this.tcpListener != null) {
        this.tcpListener.registerAddress(player.getRemoteAddress().getAddress());
      }

      this.checkLoggerToDisable();
      this.filterServer.spawnPlayer(player, new BotFilterSessionHandler(player, this));
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  private void checkCache(Map<String, CachedUser> userMap) {
    userMap.entrySet().stream()
        .filter(user -> user.getValue().getCheckTime() <= System.currentTimeMillis())
        .map(Map.Entry::getKey)
        .forEach(userMap::remove);
  }

  private void checkLoggerToEnable() {
    if (this.logsDisabled && !this.checkLoggerCps()) {
      this.logsDisabled = false;
      this.setLoggerLevel(this.initialLogLevel);
      LOGGER.warn("Re-enabling logger after attack. (see disable-log setting)");
    }
  }

  private void checkLoggerToDisable() {
    if (!this.logsDisabled && this.checkLoggerCps()) {
      this.logsDisabled = true;
      LOGGER.warn("Disabling logger during attack. (see disable-log setting)");
      this.setLoggerLevel(Level.OFF);
    }
  }

  private boolean checkLoggerCps() {
    return this.checkCpsLimit(Settings.IMP.MAIN.FILTER_AUTO_TOGGLE.DISABLE_LOG)
        || this.checkPpsLimit(Settings.IMP.MAIN.FILTER_AUTO_TOGGLE.DISABLE_LOG);
  }

  private void setLoggerLevel(Level level) {
    Configurator.setRootLevel(level);
  }

  public boolean checkCpsLimit(int limit) {
    if (limit != -1) {
      return limit <= this.statistics.getConnections();
    } else {
      return false;
    }
  }

  public boolean checkPpsLimit(int limit) {
    if (limit != -1) {
      return limit <= this.statistics.getPings();
    } else {
      return false;
    }
  }

  public File getFile(String filename) {
    File dataDirectoryFile = this.dataDirectory.resolve(filename).toFile();
    if (dataDirectoryFile.exists()) {
      return dataDirectoryFile;
    } else {
      File rootFile = new File(filename);
      if (rootFile.exists()) {
        return rootFile;
      } else {
        throw new IOError(new FileNotFoundException("File \"" + filename + "\" cannot be founded!"));
      }
    }
  }

  public ProxyServer getServer() {
    return this.server;
  }

  public LimboFactory getLimboFactory() {
    return this.limboFactory;
  }

  public PacketFactory getPacketFactory() {
    return this.packetFactory;
  }

  public CachedPackets getPackets() {
    return this.packets;
  }

  public Statistics getStatistics() {
    return this.statistics;
  }

  public TcpListener getTcpListener() {
    return this.tcpListener;
  }

  public CaptchaHolder getNextCaptcha() {
    return this.generator.getNextCaptcha();
  }

  public VirtualWorld getFilterWorld() {
    return this.filterWorld;
  }

  private static void setLogger(Logger logger) {
    LOGGER = logger;
  }

  public static Logger getLogger() {
    return LOGGER;
  }

  private static void setSerializer(Serializer serializer) {
    SERIALIZER = serializer;
  }

  public static Serializer getSerializer() {
    return SERIALIZER;
  }

  private static class CachedUser {

    private final InetAddress inetAddress;
    private final long checkTime;

    public CachedUser(InetAddress inetAddress, long checkTime) {
      this.inetAddress = inetAddress;
      this.checkTime = checkTime;
    }

    public InetAddress getInetAddress() {
      return this.inetAddress;
    }

    public long getCheckTime() {
      return this.checkTime;
    }
  }
}
