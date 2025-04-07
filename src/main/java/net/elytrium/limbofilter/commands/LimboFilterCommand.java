/*
 * Copyright (C) 2021 - 2025 Elytrium
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limbofilter.LimboFilter;
import net.elytrium.limbofilter.Settings;
import net.elytrium.limbofilter.stats.Statistics;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class LimboFilterCommand implements SimpleCommand {

  private static final List<UUID> PLAYERS_WITH_STATS = new ArrayList<>();

  private static final List<Component> HELP_MESSAGE = List.of(
      Component.text("This server is using LimboFilter and LimboAPI.", NamedTextColor.YELLOW),
      Component.text("(C) 2021 - 2023 Elytrium", NamedTextColor.YELLOW),
      Component.text("https://elytrium.net/github/", NamedTextColor.GREEN),
      Component.empty()
  );

  private static final Component AVAILABLE_SUBCOMMANDS_MESSAGE = Component.text("Available subcommands:", NamedTextColor.WHITE);
  private static final Component NO_AVAILABLE_SUBCOMMANDS_MESSAGE = Component.text("There is no available subcommands for you.", NamedTextColor.WHITE);

  private static ScheduledTask STATS_TASK;

  private final LimboFilter plugin;

  private final Component reloadComponent;
  private final Component statsEnabledComponent;
  private final Component statsDisabledComponent;

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
    this.reloadComponent = serializer.deserialize(Settings.IMP.MAIN.STRINGS.RELOAD);
    this.statsEnabledComponent = serializer.deserialize(Settings.IMP.MAIN.STRINGS.STATS_ENABLED);
    this.statsDisabledComponent = serializer.deserialize(Settings.IMP.MAIN.STRINGS.STATS_DISABLED);
  }

  @Override
  public List<String> suggest(SimpleCommand.Invocation invocation) {
    CommandSource source = invocation.source();
    String[] args = invocation.arguments();

    if (args.length == 0) {
      return Arrays.stream(Subcommand.values())
          .filter(command -> command.hasPermission(source))
          .map(Subcommand::getCommand)
          .collect(Collectors.toList());
    } else if (args.length == 1) {
      String argument = args[0];
      return Arrays.stream(Subcommand.values())
          .filter(command -> command.hasPermission(source))
          .map(Subcommand::getCommand)
          .filter(str -> str.regionMatches(true, 0, argument, 0, argument.length()))
          .collect(Collectors.toList());
    } else {
      return ImmutableList.of();
    }
  }

  @Override
  public void execute(SimpleCommand.Invocation invocation) {
    CommandSource source = invocation.source();
    String[] args = invocation.arguments();

    int argsAmount = args.length;
    if (argsAmount > 0) {
      try {
        Subcommand subcommand = Subcommand.valueOf(args[0].toUpperCase(Locale.ROOT));
        if (!subcommand.hasPermission(source)) {
          this.showHelp(source);
          return;
        }

        subcommand.executor.execute(this, source, args);
      } catch (IllegalArgumentException e) {
        this.showHelp(source);
      }
    } else {
      this.showHelp(source);
    }
  }

  @Override
  public boolean hasPermission(Invocation invocation) {
    return Settings.IMP.MAIN.COMMAND_PERMISSION_STATE.HELP
        .hasPermission(invocation.source(), "limbofilter.commands.help");
  }

  private void showHelp(CommandSource source) {
    HELP_MESSAGE.forEach(source::sendMessage);

    List<Subcommand> availableSubcommands = Arrays.stream(Subcommand.values())
        .filter(command -> command.hasPermission(source))
        .collect(Collectors.toList());

    if (availableSubcommands.size() > 0) {
      source.sendMessage(AVAILABLE_SUBCOMMANDS_MESSAGE);
      availableSubcommands.forEach(command -> source.sendMessage(command.getMessageLine()));
    } else {
      source.sendMessage(NO_AVAILABLE_SUBCOMMANDS_MESSAGE);
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

  private enum Subcommand {
    RELOAD("Reload config.", Settings.IMP.MAIN.COMMAND_PERMISSION_STATE.RELOAD,
        (LimboFilterCommand parent, CommandSource source, String[] args) -> {
          parent.plugin.reload();
          source.sendMessage(parent.reloadComponent);
        }),
    STATS("Enable/Disable statistics of connections and blocked bots.", Settings.IMP.MAIN.COMMAND_PERMISSION_STATE.STATS,
        (LimboFilterCommand parent, CommandSource source, String[] args) -> {
          if (source instanceof Player) {
            Player player = (Player) source;
            UUID playerUuid = player.getUniqueId();
            if (PLAYERS_WITH_STATS.contains(playerUuid)) {
              PLAYERS_WITH_STATS.remove(playerUuid);
              source.sendMessage(parent.statsDisabledComponent);
            } else {
              PLAYERS_WITH_STATS.add(playerUuid);
              source.sendMessage(parent.statsEnabledComponent);
            }
          } else {
            source.sendMessage(parent.createStatsComponent(null, -1));
          }
        });

    private final String command;
    private final String description;
    private final CommandPermissionState permissionState;
    private final SubcommandExecutor executor;

    Subcommand(String description, CommandPermissionState permissionState, SubcommandExecutor executor) {
      this.permissionState = permissionState;
      this.command = this.name().toLowerCase(Locale.ROOT);
      this.description = description;
      this.executor = executor;
    }

    public boolean hasPermission(CommandSource source) {
      return this.permissionState.hasPermission(source, "limbofilter.admin." + this.command);
    }

    public Component getMessageLine() {
      return Component.textOfChildren(
          Component.text("  /limbofilter " + this.command, NamedTextColor.GREEN),
          Component.text(" - ", NamedTextColor.DARK_GRAY),
          Component.text(this.description, NamedTextColor.YELLOW)
      );
    }

    public String getCommand() {
      return this.command;
    }
  }

  private interface SubcommandExecutor {
    void execute(LimboFilterCommand parent, CommandSource source, String[] args);
  }
}
