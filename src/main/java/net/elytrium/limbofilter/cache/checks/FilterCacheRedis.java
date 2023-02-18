package net.elytrium.limbofilter.cache.checks;

import net.elytrium.limbofilter.LimboFilter;
import net.elytrium.limbofilter.Settings;
import net.elytrium.limbofilter.storage.RedisStorage;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class FilterCacheRedis implements FilterCache {
    private static final String CACHED_SET_KEY = "limbofilter:cachedchecks:%s";
    private static final String CACHED_FIELD_ADDRESS = "inetAddress";
    private static final String CACHED_FIELD_TIME = "checkTime";

    @Override
    public void clear() {
        try (var jedis = RedisStorage.getInstance().connection()) {
            var transaction = jedis.multi();
            transaction.del(getKeyFor("*"));
            transaction.exec();
        }
    }

    // TODO: Probably can remove checkTime as it's not used with this storage?
    @Override
    public void put(String playerUsername, LimboFilter.CachedUser user) {
        try (var jedis = RedisStorage.getInstance().connection()) {
            var transaction = jedis.multi();
            transaction.hmset(getKeyFor(playerUsername), Map.of(
                    CACHED_FIELD_ADDRESS, user.getInetAddress().toString(),
                    CACHED_FIELD_TIME, Long.toString(user.getCheckTime())));
            // Automatic cache eviction
            transaction.expire(getKeyFor(playerUsername), TimeUnit.SECONDS.convert(Settings.IMP.MAIN.PURGE_CACHE_MILLIS, TimeUnit.MILLISECONDS));
            transaction.exec();
        }
    }

    @Override
    public void remove(String playerUsername) {
        try (var jedis = RedisStorage.getInstance().connection()) {
            var transaction = jedis.multi();
            transaction.del(getKeyFor(playerUsername));
            transaction.exec();
        }
    }

    @Override
    public void removeMultiple(Collection<String> toRemove) {
        try (var jedis = RedisStorage.getInstance().connection()) {
            var transaction = jedis.multi();
            toRemove.forEach(playerUsername -> transaction.del(getKeyFor(playerUsername)));
            transaction.exec();
        }
    }

    @Override
    public LimboFilter.CachedUser get(String playerUsername) {
        try (var jedis = RedisStorage.getInstance().connection()) {
            var transaction = jedis.multi();
            var playerAddress = transaction.hget(getKeyFor(playerUsername), CACHED_FIELD_ADDRESS);
            var cacheTime = transaction.hget(getKeyFor(playerUsername), CACHED_FIELD_TIME);
            transaction.exec();
            return new LimboFilter.CachedUser(InetAddress.getByName(playerAddress.get()), Long.parseLong(cacheTime.get()));
        } catch (Exception e) {
            throw new IllegalStateException( "Couldn't parse user address from storage", e);
        }
    }

    @Override
    public boolean containsKey(String playerUsername) {
        try (var jedis = RedisStorage.getInstance().connection()) {
            var transaction = jedis.multi();
            var result = transaction.hexists(getKeyFor(playerUsername), CACHED_FIELD_ADDRESS);
            transaction.exec();
            return result.get();
        }
    }

    @Override
    public void checkCache() {
        // We use redis expire so nothing here
    }

    private static String getKeyFor(String string) {
        return String.format(CACHED_SET_KEY, string);
    }
}
