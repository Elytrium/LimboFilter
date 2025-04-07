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

package net.elytrium.limbofilter.stats;

import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import net.elytrium.limbofilter.LimboFilter;
import net.elytrium.limbofilter.Settings;

public class Statistics {

  private final LongAdder blockedConnections = new LongAdder();
  private final LongAdder connections = new LongAdder();
  private final LongAdder pings = new LongAdder();
  private final AtomicLong interpolatedCpsBefore = new AtomicLong();
  private final AtomicLong interpolatedPpsBefore = new AtomicLong();
  private final List<ScheduledTask> scheduledTaskList = new LinkedList<>();
  private final Map<InetAddress, Integer> pingMap = new HashMap<>();

  public void addBlockedConnection() {
    this.blockedConnections.increment();
  }

  public void addConnection() {
    this.connections.add(Settings.IMP.MAIN.UNIT_OF_TIME_CPS * 2L);
  }

  public void addPing() {
    this.pings.add(Settings.IMP.MAIN.UNIT_OF_TIME_CPS * 2L);
  }

  public long getBlockedConnections() {
    return this.blockedConnections.longValue();
  }

  public long getConnections() {
    return this.connections.longValue() / Settings.IMP.MAIN.UNIT_OF_TIME_CPS / 2L;
  }

  public long getPings() {
    return this.pings.longValue() / Settings.IMP.MAIN.UNIT_OF_TIME_CPS / 2L;
  }

  public long getTotalConnection() {
    return this.getPings() + this.getConnections();
  }

  public void restartUpdateTasks(LimboFilter plugin, Scheduler scheduler) {
    synchronized (this.scheduledTaskList) {
      this.scheduledTaskList.forEach(ScheduledTask::cancel);
      this.scheduledTaskList.clear();

      this.startUpdatingCps(plugin, scheduler);
      this.startUpdatingPps(plugin, scheduler);
    }
  }

  private void startUpdatingCps(LimboFilter plugin, Scheduler scheduler) {
    long delayInterpolate = Settings.IMP.MAIN.UNIT_OF_TIME_CPS * 1000L;

    this.scheduledTaskList.add(scheduler
        .buildTask(plugin, () -> this.interpolatedCpsBefore.set(Statistics.this.connections.longValue() / Settings.IMP.MAIN.UNIT_OF_TIME_CPS / 2L))
        .delay(delayInterpolate, TimeUnit.MILLISECONDS)
        .repeat(delayInterpolate, TimeUnit.MILLISECONDS)
        .schedule());

    long delay = delayInterpolate / Settings.IMP.MAIN.UNIT_OF_TIME_CPS / 2L;

    this.scheduledTaskList.add(scheduler
        .buildTask(plugin, () -> {
          long current = Statistics.this.connections.longValue();
          long before = Statistics.this.interpolatedCpsBefore.get();

          if (current >= before) {
            Statistics.this.connections.add(-before);
          }
        })
        .delay(delay, TimeUnit.MILLISECONDS)
        .repeat(delay, TimeUnit.MILLISECONDS)
        .schedule());
  }

  private void startUpdatingPps(LimboFilter plugin, Scheduler scheduler) {
    long delayInterpolate = Settings.IMP.MAIN.UNIT_OF_TIME_PPS * 1000L;

    this.scheduledTaskList.add(scheduler
        .buildTask(plugin, () -> this.interpolatedPpsBefore.set(Statistics.this.pings.longValue() / Settings.IMP.MAIN.UNIT_OF_TIME_PPS / 2L))
        .delay(delayInterpolate, TimeUnit.MILLISECONDS)
        .repeat(delayInterpolate, TimeUnit.MILLISECONDS)
        .schedule());

    long delay = delayInterpolate / Settings.IMP.MAIN.UNIT_OF_TIME_PPS / 2L;

    this.scheduledTaskList.add(scheduler
        .buildTask(plugin, () -> {
          long current = Statistics.this.pings.longValue();
          long before = Statistics.this.interpolatedPpsBefore.get();

          if (current >= before) {
            Statistics.this.pings.add(-before);
          }
        })
        .delay(delay, TimeUnit.MILLISECONDS)
        .repeat(delay, TimeUnit.MILLISECONDS)
        .schedule());
  }

  public void updatePing(InetAddress address, int currentPing) {
    this.pingMap.merge(address, currentPing, (previousPing, newPing) -> (previousPing * 3 + newPing) / 4);
  }

  public int getPing(InetAddress address) {
    Integer ping = this.pingMap.get(address);

    if (ping == null) {
      return -1;
    } else {
      return ping;
    }
  }

  public void removeAddress(InetAddress address) {
    this.pingMap.remove(address);
  }
}
