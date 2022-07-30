package io.odpf.depot.redis;


import com.timgroup.statsd.NoOpStatsDClient;
import io.odpf.depot.OdpfSink;
import io.odpf.depot.bigquery.handler.ErrorHandler;
import io.odpf.depot.config.BigQuerySinkConfig;
import io.odpf.depot.config.RedisSinkConfig;
import io.odpf.depot.metrics.Instrumentation;
import io.odpf.depot.metrics.StatsDReporter;
import io.odpf.depot.redis.client.RedisClient;
import io.odpf.depot.redis.client.RedisClientFactory;
import io.odpf.depot.redis.parsers.RedisParser;
import io.odpf.depot.redis.parsers.RedisParserFactory;
import io.odpf.stencil.client.StencilClient;
import org.aeonbits.owner.ConfigFactory;

import java.util.Map;
import java.util.function.Function;

public class RedisSinkFactory {

    private final RedisSinkConfig sinkConfig;

    private final StatsDReporter statsDReporter;
    private RedisParser redisParser;
    private RedisClient redisClient;

    private Instrumentation instrumentation;


    public RedisSinkFactory(RedisSinkConfig sinkConfig, StatsDReporter statsDReporter) {
        this.sinkConfig = sinkConfig;
        this.statsDReporter = statsDReporter;
    }

    public void init() {
        Instrumentation instrumentation = new Instrumentation(statsDReporter, RedisSinkFactory.class);
        String redisConfig = String.format("\n\tredis.urls = %s\n\tredis.key.template = %s\n\tredis.sink.type = %s"
                        + "\n\tredis.list.data.proto.index = %s\n\tredis.ttl.type = %s\n\tredis.ttl.value = %d",
                sinkConfig.getSinkRedisUrls(),
                sinkConfig.getSinkRedisKeyTemplate(),
                sinkConfig.getSinkRedisDataType().toString(),
                sinkConfig.getSinkRedisListDataProtoIndex(),
                sinkConfig.getSinkRedisTtlType().toString(),
                sinkConfig.getSinkRedisTtlValue());
        instrumentation.logDebug(redisConfig);
        instrumentation.logInfo("Redis server type = {}", sinkConfig.getSinkRedisDeploymentType());

        RedisClientFactory redisClientFactory = new RedisClientFactory(statsDReporter, sinkConfig);
        this.redisClient = redisClientFactory.getClient();
        this.redisParser = RedisParserFactory.getParser(sinkConfig, statsDReporter);
        instrumentation.logInfo("Connection to redis established successfully");
    }

    public OdpfSink create(Map<String, String> configuration, StatsDReporter statsDReporter) {
        return new RedisSink(redisClient, redisParser, instrumentation);
    }
}