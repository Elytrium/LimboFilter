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

package net.elytrium.limbofilter.listener;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.event.query.ProxyQueryEvent;
import com.velocitypowered.api.proxy.Player;
import java.net.InetSocketAddress;
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent;
import net.elytrium.limbofilter.LimboFilter;
import net.elytrium.limbofilter.Settings;
import net.elytrium.limbofilter.stats.Statistics;

public class FilterListener {

  private final LimboFilter plugin;

  public FilterListener(LimboFilter plugin) {
    this.plugin = plugin;
  }

  @Subscribe(order = PostOrder.FIRST)
  public void onProxyConnect(PreLoginEvent event) {
    this.plugin.getStatistics().addConnection();

    if (this.plugin.checkCpsLimit(Settings.IMP.MAIN.FILTER_AUTO_TOGGLE.ONLINE_MODE_VERIFY)
        && this.plugin.shouldCheck(event.getUsername(), event.getConnection().getRemoteAddress().getAddress())) {
      event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
    }
  }

  @Subscribe
  public void onProxyDisconnect(DisconnectEvent event) {
    InetSocketAddress address = event.getPlayer().getRemoteAddress();
    if (address != null) {
      Statistics statistics = this.plugin.getStatistics();
      if (statistics != null) {
        statistics.removeAddress(address.getAddress());
      }
    }
  }

  @Subscribe(order = PostOrder.FIRST)
  public void onLogin(LoginLimboRegisterEvent event) {
    Player player = event.getPlayer();
    if (this.plugin.shouldCheck(player)) {
      event.addOnJoinCallback(() -> this.plugin.sendToFilterServer(player));
    }
  }

  @Subscribe(order = PostOrder.LAST)
  public void onPing(ProxyPingEvent event) {
    if (this.plugin.checkPpsLimit(Settings.IMP.MAIN.FILTER_AUTO_TOGGLE.DISABLE_MOTD_PICTURE)) {
      event.setPing(event.getPing().asBuilder().clearFavicon().build());
    }

    this.plugin.getStatistics().addPing();
  }

  @Subscribe
  public void onQuery(ProxyQueryEvent event) {
    this.plugin.getStatistics().addConnection();
  }
}
