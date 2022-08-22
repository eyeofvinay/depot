package io.odpf.depot.redis.entry;

import io.odpf.depot.metrics.Instrumentation;
import io.odpf.depot.redis.client.response.RedisClusterResponse;
import io.odpf.depot.redis.client.response.RedisStandaloneResponse;
import io.odpf.depot.redis.ttl.RedisTtl;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.exceptions.JedisException;

@AllArgsConstructor
@EqualsAndHashCode
public class RedisKeyValueEntry implements RedisEntry {
    private final String key;
    private final String value;
    @EqualsAndHashCode.Exclude
    private final Instrumentation instrumentation;

    @Override
    public RedisStandaloneResponse send(Pipeline jedisPipelined, RedisTtl redisTTL) {
        instrumentation.logDebug("key: {}, value: {}", key, value);
        Response<String> response = jedisPipelined.set(key, value);
        redisTTL.setTtl(jedisPipelined, key);
        return new RedisStandaloneResponse(response);
    }

    @Override
    public RedisClusterResponse send(JedisCluster jedisCluster, RedisTtl redisTTL) {
        instrumentation.logDebug("key: {}, value: {}", key, value);
        try {
            String set = jedisCluster.set(key, value);
            redisTTL.setTtl(jedisCluster, key);
            return new RedisClusterResponse(set, false);
        } catch (JedisException e) {
            return new RedisClusterResponse(e.getMessage(), true);
        }
    }

    @Override
    public String toString() {
        return String.format("RedisKeyValueEntry: Key %s, Value %s", key, value);
    }
}