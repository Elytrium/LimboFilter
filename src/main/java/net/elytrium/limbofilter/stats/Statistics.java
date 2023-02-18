package net.elytrium.limbofilter.stats;

import com.velocitypowered.api.scheduler.Scheduler;
import net.elytrium.limbofilter.LimboFilter;

import java.net.InetAddress;

public interface Statistics {
    void addBlockedConnection();

    void addConnection();

    void addPing();

    long getBlockedConnections();

    long getConnections();

    long getPings();

    long getTotalConnection();

    void restartUpdateTasks(LimboFilter plugin, Scheduler scheduler);

    void updatePing(InetAddress address, int currentPing);

    int getPing(InetAddress address);

    void removeAddress(InetAddress address);
}
