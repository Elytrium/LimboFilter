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

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.elytrium.java.commons.mc.velocity.commands.SuggestUtils;
import net.elytrium.limbofilter.LimboFilter;
import net.elytrium.limbofilter.Settings;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class SendFilterCommand implements SimpleCommand {
  private final LimboFilter plugin;

  public SendFilterCommand(LimboFilter plugin) {
    this.plugin = plugin;
  }

  @Override
  public void execute(Invocation invocation) {
    String[] args = invocation.arguments();
    for (String arg : args) {
      Optional<RegisteredServer> server = this.plugin.getServer().getServer(arg);

      if (server.isPresent()) {
        Collection<Player> players = server.get().getPlayersConnected();
        players.forEach(this.plugin::sendToFilterServer);
        invocation.source().sendMessage(
            LegacyComponentSerializer.legacyAmpersand().deserialize(
                MessageFormat.format(Settings.IMP.MAIN.STRINGS.SEND_COMMAND_SERVER_SUCCESS, players.size(), arg)));
      } else {
        Optional<Player> player = this.plugin.getServer().getPlayer(arg);

        if (player.isPresent()) {
          this.plugin.sendToFilterServer(player.get());
          invocation.source().sendMessage(
              LegacyComponentSerializer.legacyAmpersand().deserialize(
                  MessageFormat.format(Settings.IMP.MAIN.STRINGS.SEND_COMMAND_SUCCESS, player.get().getUsername())));
        } else {
          invocation.source().sendMessage(
              LegacyComponentSerializer.legacyAmpersand().deserialize(
                  MessageFormat.format(Settings.IMP.MAIN.STRINGS.SEND_COMMAND_FAIL, arg)));
        }
      }
    }
  }

  @Override
  public List<String> suggest(Invocation invocation) {
    String[] args = invocation.arguments();
    return SuggestUtils.suggestServersAndPlayers(this.plugin.getServer(), args, args.length);
  }

  @Override
  public boolean hasPermission(Invocation invocation) {
    return invocation.source().getPermissionValue("limbofilter.sendcaptcha") == Tristate.TRUE;
  }

}
