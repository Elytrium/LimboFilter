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

package net.elytrium.limbofilter.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.elytrium.java.commons.mc.serialization.Serializer;
import net.elytrium.java.commons.mc.velocity.commands.SuggestUtils;
import net.elytrium.limbofilter.LimboFilter;
import net.elytrium.limbofilter.Settings;

public class SendFilterCommand implements SimpleCommand {

  private final LimboFilter plugin;

  public SendFilterCommand(LimboFilter plugin) {
    this.plugin = plugin;
  }

  @Override
  public List<String> suggest(SimpleCommand.Invocation invocation) {
    String[] args = invocation.arguments();
    return SuggestUtils.suggestServersAndPlayers(this.plugin.getServer(), args, args.length);
  }

  @Override
  public void execute(SimpleCommand.Invocation invocation) {
    CommandSource source = invocation.source();
    String[] args = invocation.arguments();

    ProxyServer server = this.plugin.getServer();
    Serializer serializer = LimboFilter.getSerializer();
    for (String target : args) {
      Optional<RegisteredServer> registeredServer = server.getServer(target);
      if (registeredServer.isPresent()) {
        Collection<Player> players = registeredServer.get().getPlayersConnected();
        players.forEach(this.plugin::resetCacheForFilterUser);
        players.forEach(this.plugin::sendToFilterServer);
        source.sendMessage(serializer.deserialize(MessageFormat.format(Settings.IMP.MAIN.STRINGS.SEND_SERVER_SUCCESSFUL, players.size(), target)));
      } else {
        Optional<Player> optionalPlayer = server.getPlayer(target);
        if (optionalPlayer.isPresent()) {
          Player player = optionalPlayer.get();
          this.plugin.resetCacheForFilterUser(player);
          this.plugin.sendToFilterServer(player);
          source.sendMessage(serializer.deserialize(MessageFormat.format(Settings.IMP.MAIN.STRINGS.SEND_PLAYER_SUCCESSFUL, player.getUsername())));
        } else {
          source.sendMessage(serializer.deserialize(MessageFormat.format(Settings.IMP.MAIN.STRINGS.SEND_FAILED, target)));
        }
      }
    }
  }

  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    return invocation.source().hasPermission("limbofilter.commands.sendfilter");
  }
}
