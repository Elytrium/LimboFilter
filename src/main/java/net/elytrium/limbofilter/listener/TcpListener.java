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

package net.elytrium.limbofilter.listener;

import com.velocitypowered.proxy.config.VelocityConfiguration;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.elytrium.limbofilter.LimboFilter;
import net.elytrium.limbofilter.Settings;
import org.pcap4j.core.BpfProgram;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PacketListener;
import org.pcap4j.core.PcapAddress;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.TcpPacket;

public class TcpListener {

  private final LimboFilter plugin;
  private final Map<InetAddress, Long> tempPingTimestamp = new HashMap<>();

  public TcpListener(LimboFilter plugin) {
    this.plugin = plugin;
  }

  public void start() throws PcapNativeException, NotOpenException {
    Set<InetAddress> localAddresses = Pcaps.findAllDevs().stream().flatMap(e -> e.getAddresses().stream()).map(PcapAddress::getAddress)
        .collect(Collectors.toSet());

    PcapNetworkInterface networkInterface = Pcaps.getDevByName(Settings.IMP.MAIN.TCP_LISTENER.INTERFACE_NAME);
    PcapHandle handle = networkInterface.openLive(Settings.IMP.MAIN.TCP_LISTENER.SNAPLEN, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS,
        Settings.IMP.MAIN.TCP_LISTENER.TIMEOUT
    );

    int port = ((VelocityConfiguration) this.plugin.getServer().getConfiguration()).getBind().getPort();
    handle.setFilter("tcp and (dst port " + port + " or src port " + port + ")", BpfProgram.BpfCompileMode.OPTIMIZE);

    new Thread(() -> {
      try {
        handle.loop(Settings.IMP.MAIN.TCP_LISTENER.PACKET_COUNT, (PacketListener) rawPacket -> {
          IpPacket ipPacket = (IpPacket) rawPacket;
          IpPacket.IpHeader ipHeader = ipPacket.getHeader();

          TcpPacket tcpPacket = (TcpPacket) ipPacket.getPayload();
          TcpPacket.TcpHeader tcpHeader = tcpPacket.getHeader();

          if (localAddresses.contains(ipHeader.getSrcAddr()) && tcpHeader.getPsh() && tcpHeader.getAck()) {
            this.tempPingTimestamp.put(ipHeader.getDstAddr(), System.currentTimeMillis());
          }

          if (localAddresses.contains(ipHeader.getDstAddr()) && tcpHeader.getAck()) {
            Long tempPingTimestamp = this.tempPingTimestamp.get(ipHeader.getDstAddr());
            if (tempPingTimestamp != null) {
              this.plugin.getStatistics().updatePing(ipHeader.getSrcAddr(), (int) (System.currentTimeMillis() - tempPingTimestamp));
            }
          }
        });
      } catch (PcapNativeException | InterruptedException | NotOpenException e) {
        e.printStackTrace();
      }
    }).start();
  }
}
