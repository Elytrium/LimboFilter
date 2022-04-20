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
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.elytrium.limbofilter.LimboFilter;
import net.elytrium.limbofilter.Settings;
import net.elytrium.limbofilter.stats.Statistics;
import net.kyori.adventure.text.Component;

public class LimboFilterCommand implements SimpleCommand {

  private final LimboFilter plugin;
  private final List<UUID> playersWithStats = Collections.synchronizedList(new ArrayList<>());

  public LimboFilterCommand(LimboFilter plugin) {
    this.plugin = plugin;

    ProxyServer server = plugin.getServer();
    server.getScheduler().buildTask(this.plugin, () -> {
      try {
        this.playersWithStats
            .stream()
            .map(server::getPlayer)
            .forEach(optionalPlayer -> optionalPlayer.ifPresent(player -> player.sendActionBar(this.createStatsComponent(player.getPing()))));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }).repeat(1, TimeUnit.SECONDS).schedule();
  }

  @Override
  public List<String> suggest(SimpleCommand.Invocation invocation) {
    CommandSource source = invocation.source();
    String[] args = invocation.arguments();

    if (args.length == 0) {
      return this.getSubCommands()
          .filter(cmd -> source.hasPermission("limbofilter.commands." + cmd))
          .collect(Collectors.toList());
    } else if (args.length == 1) {
      return this.getSubCommands()
          .filter(cmd -> source.hasPermission("limbofilter.commands." + cmd))
          .filter(str -> str.regionMatches(true, 0, args[0], 0, args[0].length()))
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
      switch (args[0].toLowerCase(Locale.ROOT)) {
        case "reload": {
          if (source.hasPermission("limbofilter.commands.reload")) {
            try {
              this.plugin.reload();
              source.sendMessage(LimboFilter.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.RELOAD));
            } catch (Exception e) {
              source.sendMessage(LimboFilter.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.RELOAD_FAILED));
              e.printStackTrace();
            }
          } else {
            this.showHelp(source);
          }

          return;
        }
        case "stats": {
          if (source instanceof Player) {
            if (source.hasPermission("limbofilter.commands.stats")) {
              ConnectedPlayer player = (ConnectedPlayer) source;
              if (!this.playersWithStats.contains(player.getUniqueId())) {
                source.sendMessage(LimboFilter.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.STATS_ENABLED));
                this.playersWithStats.add(player.getUniqueId());
              } else {
                source.sendMessage(LimboFilter.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.STATS_DISABLED));
                this.playersWithStats.remove(player.getUniqueId());
              }
            } else {
              this.showHelp(source);
            }
          } else {
            source.sendMessage(this.createStatsComponent(-1));
          }

          return;
        }
        default: {
          this.showHelp(source);
          return;
        }
      }
    }

    this.showHelp(source);
  }

  private void showHelp(CommandSource source) {
    source.sendMessage(Component.text("§eThis server is using LimboFilter and LimboAPI"));
    source.sendMessage(Component.text("§e(C) 2021 - 2022 Elytrium"));
    source.sendMessage(Component.text("§ahttps://ely.su/github/"));
    source.sendMessage(Component.text("§r"));
    source.sendMessage(Component.text("§fAvailable subcommands:"));
    // Java moment
    this.getSubCommands()
        .filter(cmd -> source.hasPermission("limbofilter.commands." + cmd))
        .forEach(cmd -> {
          switch (cmd) {
            case "reload": {
              source.sendMessage(Component.text("    §a/limbofilter reload §8- §eReload config."));
              break;
            }
            case "stats": {
              source.sendMessage(Component.text("    §a/limbofilter stats §8- §eEnable/Disable statistics of connections and blocked bots."));
              break;
            }
            default: {
              throw new AssertionError();
            }
          }
        });
  }

  private Stream<String> getSubCommands() {
    return Stream.of("reload", "stats");
  }

  private Component createStatsComponent(long ping) {
    Statistics statistics = this.plugin.getStatistics();
    return LimboFilter.getSerializer().deserialize(
        MessageFormat.format(
            Settings.IMP.MAIN.STRINGS.STATS_FORMAT,
            statistics.getBlockedConnections(),
            statistics.getConnections() + "/" + Settings.IMP.MAIN.UNIT_OF_TIME_CPS,
            statistics.getPings() + "/" + Settings.IMP.MAIN.UNIT_OF_TIME_PPS,
            statistics.getTotalConnection(),
            ping
        )
    );
  }
}
