/*
 * Copyright (C) 2021 Elytrium
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

package net.elytrium.limbofilter.cache.captcha;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.elytrium.limboapi.api.protocol.PreparedPacket;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public class CaptchaHandler {

  private final MinecraftPacket mapPacket;
  private final MinecraftPacket[] mapPackets17;
  private final PreparedPacket preparedMapPacket;
  private final String answer;

  public CaptchaHandler(MinecraftPacket mapPacket, MinecraftPacket[] mapPackets17, String answer) {
    this.mapPacket = mapPacket;
    this.mapPackets17 = mapPackets17;
    this.preparedMapPacket = null;
    this.answer = answer;
  }

  public CaptchaHandler(PreparedPacket preparedMapPacket, String answer) {
    this.mapPacket = null;
    this.mapPackets17 = null;
    this.preparedMapPacket = preparedMapPacket;
    this.answer = answer;
  }

  public Object[] getMapPacket(ProtocolVersion version) {
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_8) >= 0) {
      return this.mapPacket == null ? new Object[] {this.preparedMapPacket} : new Object[] {this.mapPacket};
    } else {
      return this.mapPackets17 == null ? new Object[] {this.preparedMapPacket} : this.mapPackets17;
    }
  }

  public String getAnswer() {
    return this.answer;
  }
}
