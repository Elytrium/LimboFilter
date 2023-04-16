package net.elytrium.limbofilter.event;

import net.elytrium.limboapi.api.player.LimboPlayer;

public class CheckFinishedEvent {

  public final LimboPlayer player;

  public CheckFinishedEvent(LimboPlayer player) {
    this.player = player;
  }
}
