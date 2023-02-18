package net.elytrium.limbofilter.storage;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisStorage {

    private static final RedisStorage instance;

    private final JedisPool jedisPool;

    private RedisStorage() {
        jedisPool = new JedisPool("localhost", 6379);
    }

    static {
        try {
            instance = new RedisStorage();
        } catch (Exception e) {
            throw new RuntimeException("Exception occurred while connecting to Redis");
        }
    }

    public static RedisStorage getInstance() {
        return instance;
    }

    public Jedis connection() {
        return jedisPool.getResource();
    }
}
