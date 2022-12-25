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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.elytrium.limbofilter.LimboFilter;
import net.elytrium.limbofilter.Settings;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
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
import org.pcap4j.packet.factory.statik.services.StaticPacketFactoryBinderProvider;

public class TcpListener {

  private final LimboFilter plugin;
  private final Map<InetAddress, TcpAwaitingPacket> tempPingTimestamp = new HashMap<>();
  @MonotonicNonNull
  private Thread thread;

  static {
    Objects.requireNonNull(StaticPacketFactoryBinderProvider.class);
  }

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

    this.thread = new Thread(() -> {
      try {
        Thread.currentThread().setContextClassLoader(LimboFilter.class.getClassLoader());
        long listenDelay = Settings.IMP.MAIN.TCP_LISTENER.LISTEN_DELAY;

        handle.loop(-1, (PacketListener) rawPacket -> {
          IpPacket ipPacket = rawPacket.get(IpPacket.class);
          IpPacket.IpHeader ipHeader = ipPacket.getHeader();

          TcpPacket tcpPacket = ipPacket.getPayload().get(TcpPacket.class);
          TcpPacket.TcpHeader tcpHeader = tcpPacket.getHeader();

          if (localAddresses.contains(ipHeader.getSrcAddr()) && tcpHeader.getPsh() && tcpHeader.getAck()) {
            TcpAwaitingPacket previousPacket = this.tempPingTimestamp.get(ipHeader.getDstAddr());
            if (previousPacket != null) {
              long currentTime = System.currentTimeMillis();
              if (previousPacket.time + listenDelay <= currentTime) {
                previousPacket.seq = tcpHeader.getAcknowledgmentNumber();
                previousPacket.time = currentTime;
              }
            }
          }

          if (localAddresses.contains(ipHeader.getDstAddr()) && tcpHeader.getAck()) {
            TcpAwaitingPacket awaitingPacket = this.tempPingTimestamp.get(ipHeader.getSrcAddr());
            if (awaitingPacket != null && awaitingPacket.seq == tcpHeader.getSequenceNumber()) {
              int pingDiff = (int) (System.currentTimeMillis() - awaitingPacket.time);
              if (pingDiff > 2) {
                this.plugin.getStatistics().updatePing(ipHeader.getSrcAddr(), pingDiff);
              }
            }
          }
        });
      } catch (PcapNativeException | InterruptedException | NotOpenException e) {
        e.printStackTrace();
      }
    });

    this.thread.start();
  }

  public void registerAddress(InetAddress address) {
    this.tempPingTimestamp.put(address, new TcpAwaitingPacket(Integer.MIN_VALUE, 0));
  }

  public void removeAddress(InetAddress address) {
    this.tempPingTimestamp.remove(address);
  }

  public void stop() {
    this.thread.interrupt();
  }

  private static class TcpAwaitingPacket {

    private int seq;
    private long time;

    private TcpAwaitingPacket(int seq, long time) {
      this.seq = seq;
      this.time = time;
    }
  }
}
