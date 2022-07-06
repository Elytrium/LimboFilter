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

package net.elytrium.limbofilter.captcha;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import java.util.EnumMap;
import net.elytrium.limboapi.api.protocol.PreparedPacket;

public class CaptchaHolder {

  private final String answer;
  private final MinecraftPacket[] mapDataPackets17;
  private final EnumMap<ProtocolVersion, MinecraftPacket[]> mapDataPacket;
  private final PreparedPacket[] preparedMapPacket;
  private CaptchaHolder next;

  public CaptchaHolder(CaptchaHolder another) {
    this.answer = another.answer;
    this.mapDataPackets17 = another.mapDataPackets17;
    this.mapDataPacket = another.mapDataPacket;
    this.preparedMapPacket = another.preparedMapPacket;
    this.next = another.next;
  }

  public CaptchaHolder(String answer, CaptchaHolder next, MinecraftPacket[] mapDataPackets17, EnumMap<ProtocolVersion, MinecraftPacket[]> mapDataPacket) {
    this.answer = answer;
    this.next = next;
    this.mapDataPackets17 = mapDataPackets17;
    this.mapDataPacket = mapDataPacket;
    this.preparedMapPacket = null;
  }

  public CaptchaHolder(String answer, CaptchaHolder next, PreparedPacket... preparedMapPacket) {
    this.answer = answer;
    this.next = next;
    this.mapDataPackets17 = null;
    this.mapDataPacket = null;
    this.preparedMapPacket = preparedMapPacket;
  }

  public Object[] getMapPacket(ProtocolVersion version) {
    if (version.compareTo(ProtocolVersion.MINECRAFT_1_8) < 0) {
      return this.mapDataPackets17 == null ? this.preparedMapPacket : this.mapDataPackets17;
    } else {
      return this.mapDataPacket == null ? this.preparedMapPacket : this.mapDataPacket.get(version);
    }
  }

  public String getAnswer() {
    return this.answer;
  }

  public CaptchaHolder getNext() {
    return this.next;
  }

  public void setNext(CaptchaHolder next) {
    this.next = next;
  }

  public void release() {
    this.next = null;
    if (this.preparedMapPacket != null) {
      for (PreparedPacket preparedPacket : this.preparedMapPacket) {
        preparedPacket.release();
      }
    }
  }
}
