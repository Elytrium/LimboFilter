package net.elytrium.limbofilter.cache.checks;

import net.elytrium.limbofilter.LimboFilter;

import java.util.Collection;

public interface FilterCache {
    void clear();

    void put(String playerUsername, LimboFilter.CachedUser user);

    void remove(String playerUsername);

    void removeMultiple(Collection<String> toRemove);

    LimboFilter.CachedUser get(String playerUsername);

    boolean containsKey(String playerUsername);

    void checkCache();
}
