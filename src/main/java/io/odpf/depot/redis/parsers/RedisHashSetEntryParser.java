package io.odpf.depot.redis.parsers;


import io.odpf.depot.config.RedisSinkConfig;
import io.odpf.depot.message.OdpfMessageSchema;
import io.odpf.depot.message.ParsedOdpfMessage;
import io.odpf.depot.metrics.Instrumentation;
import io.odpf.depot.metrics.StatsDReporter;
import io.odpf.depot.redis.entry.RedisEntry;
import io.odpf.depot.redis.entry.RedisHashSetFieldEntry;
import io.odpf.depot.redis.util.RedisSinkUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;


/**
 * Redis hash set parser.
 */
public class RedisHashSetEntryParser implements RedisEntryParser {
    private final RedisSinkConfig redisSinkConfig;
    private final StatsDReporter statsDReporter;

    public RedisHashSetEntryParser(RedisSinkConfig redisSinkConfig, StatsDReporter statsDReporter) {
        this.redisSinkConfig = redisSinkConfig;
        this.statsDReporter = statsDReporter;
    }

    @Override
    public List<RedisEntry> getRedisEntry(ParsedOdpfMessage parsedOdpfMessage, OdpfMessageSchema schema) {
        String redisKey = RedisSinkUtils.parseTemplate(redisSinkConfig.getSinkRedisKeyTemplate(), parsedOdpfMessage, schema);
        List<RedisEntry> messageEntries = new ArrayList<>();
        Properties properties = redisSinkConfig.getSinkRedisHashsetFieldToColumnMapping();
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("Empty config SINK_REDIS_HASHSET_FIELD_TO_COLUMN_MAPPING found");
        }
        properties.stringPropertyNames().forEach(key -> {
            String value = properties.get(key).toString();
            String field = RedisSinkUtils.parseTemplate(value, parsedOdpfMessage, schema);
            String redisValue = parsedOdpfMessage.getFieldByName(key, schema).toString();
            messageEntries.add(new RedisHashSetFieldEntry(redisKey, field, redisValue, new Instrumentation(statsDReporter, RedisHashSetFieldEntry.class)));
        });
        return messageEntries;
    }
}
