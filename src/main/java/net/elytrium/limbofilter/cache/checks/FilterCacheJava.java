package net.elytrium.limbofilter.cache.checks;

import net.elytrium.limbofilter.LimboFilter;

import java.util.Collection;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

public class FilterCacheJava implements FilterCache {
    private final Map<String, LimboFilter.CachedUser> cache = new ConcurrentHashMap<>();

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void put(String playerUsername, LimboFilter.CachedUser user) {
        cache.put(playerUsername, user);
    }

    @Override
    public void remove(String playerUsername) {
        cache.remove(playerUsername);
    }

    @Override
    public LimboFilter.CachedUser get(String playerUsername) {
        return cache.get(playerUsername);
    }

    @Override
    public boolean containsKey(String playerUsername) {
        return cache.containsKey(playerUsername);
    }

    @Override
    public void checkCache() {
        cache.entrySet().stream()
                .filter(user -> user.getValue().getCheckTime() <= System.currentTimeMillis())
                .map(Map.Entry::getKey)
                .forEach(cache::remove);
    }

    @Override
    public void removeMultiple(Collection<String> toRemove) {
        toRemove.forEach(this::remove);
    }

}
