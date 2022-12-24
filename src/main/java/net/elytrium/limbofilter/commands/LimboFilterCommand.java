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

package net.elytrium.limbofilter.commands;

import com.google.common.collect.ImmutableList;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.net.InetAddress;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.elytrium.java.commons.mc.serialization.Serializer;
import net.elytrium.limbofilter.LimboFilter;
import net.elytrium.limbofilter.Settings;
import net.elytrium.limbofilter.stats.Statistics;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class LimboFilterCommand implements SimpleCommand {

  private static final List<UUID> PLAYERS_WITH_STATS = new ArrayList<>();

  private static final List<Component> HELP_MESSAGE = List.of(
      Component.text("This server is using LimboFilter and LimboAPI.", NamedTextColor.YELLOW),
      Component.text("(C) 2021 - 2022 Elytrium", NamedTextColor.YELLOW),
      Component.text("https://elytrium.net/github/", NamedTextColor.GREEN),
      Component.empty()
  );
  private static final Map<String, Component> SUBCOMMANDS = Map.of(
      "stats", Component.textOfChildren(
          Component.text("  /limbofilter stats", NamedTextColor.GREEN),
          Component.text(" - ", NamedTextColor.DARK_GRAY),
          Component.text("Enable/Disable statistics of connections and blocked bots.", NamedTextColor.YELLOW)
      ),
      "reload", Component.textOfChildren(
          Component.text("  /limbofilter reload", NamedTextColor.GREEN),
          Component.text(" - ", NamedTextColor.DARK_GRAY),
          Component.text("Reload config.", NamedTextColor.YELLOW)
      )
  );
  private static final Component AVAILABLE_SUBCOMMANDS_MESSAGE = Component.text("Available subcommands:", NamedTextColor.WHITE);
  private static final Component NO_AVAILABLE_SUBCOMMANDS_MESSAGE = Component.text("There is no available subcommands for you.", NamedTextColor.WHITE);

  private static ScheduledTask STATS_TASK;

  private final LimboFilter plugin;

  private final Component reload;
  private final Component reloadFailed;
  private final Component statsEnabled;
  private final Component statsDisabled;

  public LimboFilterCommand(LimboFilter plugin) {
    this.plugin = plugin;

    if (STATS_TASK == null) {
      ProxyServer server = this.plugin.getServer();
      STATS_TASK = server.getScheduler().buildTask(
          this.plugin,
          () -> PLAYERS_WITH_STATS.stream()
              .map(server::getPlayer)
              .forEach(optionalPlayer -> optionalPlayer.ifPresent(
                  player -> player.sendActionBar(this.createStatsComponent(player.getRemoteAddress().getAddress(), player.getPing()))))
      ).repeat(1, TimeUnit.SECONDS).schedule();
    }

    Serializer serializer = LimboFilter.getSerializer();
    this.reload = serializer.deserialize(Settings.IMP.MAIN.STRINGS.RELOAD);
    this.reloadFailed = serializer.deserialize(Settings.IMP.MAIN.STRINGS.RELOAD_FAILED);
    this.statsEnabled = serializer.deserialize(Settings.IMP.MAIN.STRINGS.STATS_ENABLED);
    this.statsDisabled = serializer.deserialize(Settings.IMP.MAIN.STRINGS.STATS_DISABLED);
  }

  @Override
  public List<String> suggest(SimpleCommand.Invocation invocation) {
    CommandSource source = invocation.source();
    String[] args = invocation.arguments();

    if (args.length == 0) {
      return SUBCOMMANDS.keySet().stream()
          .filter(command -> source.hasPermission("limbofilter.admin." + command))
          .collect(Collectors.toList());
    } else if (args.length == 1) {
      String argument = args[0];
      return SUBCOMMANDS.keySet().stream()
          .filter(command -> source.hasPermission("limbofilter.admin." + command))
          .filter(command -> command.regionMatches(true, 0, argument, 0, argument.length()))
          .collect(Collectors.toList());
    } else {
      return ImmutableList.of();
    }
  }

  @Override
  public void execute(SimpleCommand.Invocation invocation) {
    CommandSource source = invocation.source();
    String[] args = invocation.arguments();

    if (args.length == 1) {
      String command = args[0];
      if (command.equalsIgnoreCase("reload") && source.hasPermission("limbofilter.admin.reload")) {
        try {
          this.plugin.reload();
          source.sendMessage(this.reload, MessageType.SYSTEM);
        } catch (Exception e) {
          e.printStackTrace();
          source.sendMessage(this.reloadFailed, MessageType.SYSTEM);
        }

        return;
      } else if (command.equalsIgnoreCase("stats") && source.hasPermission("limbofilter.admin.stats")) {
        if (source instanceof Player) {
          Player player = (Player) source;
          UUID playerUuid = player.getUniqueId();
          if (PLAYERS_WITH_STATS.contains(playerUuid)) {
            PLAYERS_WITH_STATS.remove(playerUuid);
            source.sendMessage(this.statsDisabled, MessageType.SYSTEM);
          } else {
            PLAYERS_WITH_STATS.add(playerUuid);
            source.sendMessage(this.statsEnabled, MessageType.SYSTEM);
          }
        } else {
          source.sendMessage(this.createStatsComponent(null, -1), MessageType.SYSTEM);
        }

        return;
      }
    }

    this.showHelp(source);
  }

  private void showHelp(CommandSource source) {
    HELP_MESSAGE.forEach(message -> source.sendMessage(message, MessageType.SYSTEM));
    List<Map.Entry<String, Component>> availableSubcommands = SUBCOMMANDS.entrySet().stream()
        .filter(command -> source.hasPermission("limbofilter.admin." + command.getKey()))
        .collect(Collectors.toList());
    if (availableSubcommands.size() > 0) {
      source.sendMessage(AVAILABLE_SUBCOMMANDS_MESSAGE, MessageType.SYSTEM);
      availableSubcommands.forEach(command -> source.sendMessage(command.getValue(), MessageType.SYSTEM));
    } else {
      source.sendMessage(NO_AVAILABLE_SUBCOMMANDS_MESSAGE, MessageType.SYSTEM);
    }
  }

  private Component createStatsComponent(InetAddress address, long ping) {
    Statistics statistics = this.plugin.getStatistics();
    return LimboFilter.getSerializer().deserialize(
        MessageFormat.format(
            Settings.IMP.MAIN.STRINGS.STATS_FORMAT,
            statistics.getBlockedConnections(),
            statistics.getConnections() + "/" + Settings.IMP.MAIN.UNIT_OF_TIME_CPS,
            statistics.getPings() + "/" + Settings.IMP.MAIN.UNIT_OF_TIME_PPS,
            statistics.getTotalConnection(),
            ping,
            statistics.getPing(address)
        )
    );
  }
}
