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

public class StatisticsJava implements Statistics {

  private final LongAdder blockedConnections = new LongAdder();
  private final LongAdder connections = new LongAdder();
  private final LongAdder pings = new LongAdder();
  private final AtomicLong interpolatedCpsBefore = new AtomicLong();
  private final AtomicLong interpolatedPpsBefore = new AtomicLong();
  private final List<ScheduledTask> scheduledTaskList = new LinkedList<>();
  private final Map<InetAddress, Integer> pingMap = new HashMap<>();

  @Override
  public void addBlockedConnection() {
    this.blockedConnections.increment();
  }

  @Override
  public void addConnection() {
    this.connections.add(Settings.IMP.MAIN.UNIT_OF_TIME_CPS * 2L);
  }

  @Override
  public void addPing() {
    this.pings.add(Settings.IMP.MAIN.UNIT_OF_TIME_CPS * 2L);
  }

  @Override
  public long getBlockedConnections() {
    return this.blockedConnections.longValue();
  }

  @Override
  public long getConnections() {
    return this.connections.longValue() / Settings.IMP.MAIN.UNIT_OF_TIME_CPS / 2L;
  }

  @Override
  public long getPings() {
    return this.pings.longValue() / Settings.IMP.MAIN.UNIT_OF_TIME_CPS / 2L;
  }

  @Override
  public long getTotalConnection() {
    return this.getPings() + this.getConnections();
  }

  @Override
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
        .buildTask(plugin, () -> this.interpolatedCpsBefore.set(StatisticsJava.this.connections.longValue() / Settings.IMP.MAIN.UNIT_OF_TIME_CPS / 2L))
        .delay(delayInterpolate, TimeUnit.MILLISECONDS)
        .repeat(delayInterpolate, TimeUnit.MILLISECONDS)
        .schedule());

    long delay = delayInterpolate / Settings.IMP.MAIN.UNIT_OF_TIME_CPS / 2L;

    this.scheduledTaskList.add(scheduler
        .buildTask(plugin, () -> {
          long current = StatisticsJava.this.connections.longValue();
          long before = StatisticsJava.this.interpolatedCpsBefore.get();

          if (current >= before) {
            StatisticsJava.this.connections.add(-before);
          }
        })
        .delay(delay, TimeUnit.MILLISECONDS)
        .repeat(delay, TimeUnit.MILLISECONDS)
        .schedule());
  }

  private void startUpdatingPps(LimboFilter plugin, Scheduler scheduler) {
    long delayInterpolate = Settings.IMP.MAIN.UNIT_OF_TIME_PPS * 1000L;

    this.scheduledTaskList.add(scheduler
        .buildTask(plugin, () -> this.interpolatedPpsBefore.set(StatisticsJava.this.pings.longValue() / Settings.IMP.MAIN.UNIT_OF_TIME_PPS / 2L))
        .delay(delayInterpolate, TimeUnit.MILLISECONDS)
        .repeat(delayInterpolate, TimeUnit.MILLISECONDS)
        .schedule());

    long delay = delayInterpolate / Settings.IMP.MAIN.UNIT_OF_TIME_PPS / 2L;

    this.scheduledTaskList.add(scheduler
        .buildTask(plugin, () -> {
          long current = StatisticsJava.this.pings.longValue();
          long before = StatisticsJava.this.interpolatedPpsBefore.get();

          if (current >= before) {
            StatisticsJava.this.pings.add(-before);
          }
        })
        .delay(delay, TimeUnit.MILLISECONDS)
        .repeat(delay, TimeUnit.MILLISECONDS)
        .schedule());
  }

  @Override
  public void updatePing(InetAddress address, int currentPing) {
    this.pingMap.merge(address, currentPing, (previousPing, newPing) -> (previousPing * 3 + newPing) / 4);
  }

  @Override
  public int getPing(InetAddress address) {
    Integer ping = this.pingMap.get(address);

    if (ping == null) {
      return -1;
    } else {
      return ping;
    }
  }

  @Override
  public void removeAddress(InetAddress address) {
    this.pingMap.remove(address);
  }
}
