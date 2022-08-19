package io.odpf.depot.redis.parsers;

import com.google.protobuf.Descriptors;
import io.odpf.depot.TestBookingLogMessage;
import io.odpf.depot.TestKey;
import io.odpf.depot.TestMessage;
import io.odpf.depot.config.RedisSinkConfig;
import io.odpf.depot.message.OdpfMessage;
import io.odpf.depot.message.OdpfMessageSchema;
import io.odpf.depot.message.ParsedOdpfMessage;
import io.odpf.depot.message.SinkConnectorSchemaMessageMode;
import io.odpf.depot.message.proto.ProtoOdpfMessageParser;
import io.odpf.depot.metrics.StatsDReporter;
import io.odpf.depot.redis.entry.RedisListEntry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RedisListEntryParserTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    @Mock
    private RedisSinkConfig redisSinkConfig;
    @Mock
    private StatsDReporter statsDReporter;

    private OdpfMessage message;

    private Map<String, Descriptors.Descriptor> descriptorsMap;

    @Before
    public void setUp() throws Exception {
        TestKey testKey = TestKey.newBuilder().setOrderNumber("ORDER-1-FROM-KEY").build();
        TestMessage testMessage = TestMessage.newBuilder().setOrderNumber("test-order").setOrderDetails("ORDER-DETAILS").build();
        this.message = new OdpfMessage(testKey.toByteArray(), testMessage.toByteArray());
        descriptorsMap = new HashMap<String, Descriptors.Descriptor>() {{
            put(String.format("%s", TestKey.class.getName()), TestKey.getDescriptor());
            put(String.format("%s", TestMessage.class.getName()), TestMessage.getDescriptor());
            put(String.format("%s", TestBookingLogMessage.class.getName()), TestBookingLogMessage.getDescriptor());
        }};
    }

    private void setRedisSinkConfig(SinkConnectorSchemaMessageMode mode, String redisTemplate) {
        when(redisSinkConfig.getSinkConnectorSchemaMessageMode()).thenReturn(mode);
        when(redisSinkConfig.getSinkRedisKeyTemplate()).thenReturn(redisTemplate);
        when(redisSinkConfig.getSinkRedisListDataFieldName()).thenReturn("order_number");
    }

    @Test
    public void shouldParseStringMessageForCollectionKeyTemplateInList() throws IOException {
        setRedisSinkConfig(SinkConnectorSchemaMessageMode.LOG_MESSAGE, "Test-%s,order_number");
        SinkConnectorSchemaMessageMode mode = redisSinkConfig.getSinkConnectorSchemaMessageMode();
        String schemaClass = "io.odpf.depot.TestMessage";
        ProtoOdpfMessageParser odpfMessageParser = new ProtoOdpfMessageParser(redisSinkConfig, statsDReporter, null);
        RedisParser redisListParser = new RedisListEntryParser(odpfMessageParser, redisSinkConfig, statsDReporter);
        ParsedOdpfMessage parsedOdpfMessage = odpfMessageParser.parse(message, mode, schemaClass);
        OdpfMessageSchema schema = odpfMessageParser.getSchema(schemaClass, descriptorsMap);
        RedisListEntry redisListEntry = (RedisListEntry) redisListParser.getRedisEntry(0, parsedOdpfMessage, schema).get(0);
        RedisListEntry expectedEntry = new RedisListEntry("Test-test-order", "test-order", null, 0);
        assertEquals(expectedEntry, redisListEntry);
    }

    @Test
    public void shouldParseKeyWhenKafkaMessageParseModeSetToKey() throws IOException {
        setRedisSinkConfig(SinkConnectorSchemaMessageMode.LOG_KEY, "test-key");
        SinkConnectorSchemaMessageMode mode = redisSinkConfig.getSinkConnectorSchemaMessageMode();
        String schemaClass = "io.odpf.depot.TestKey";
        ProtoOdpfMessageParser odpfMessageParser = new ProtoOdpfMessageParser(redisSinkConfig, statsDReporter, null);
        RedisParser redisListParser = new RedisListEntryParser(odpfMessageParser, redisSinkConfig, statsDReporter);
        ParsedOdpfMessage parsedOdpfMessage = odpfMessageParser.parse(message, mode, schemaClass);
        OdpfMessageSchema schema = odpfMessageParser.getSchema(schemaClass, descriptorsMap);
        RedisListEntry redisListEntry = (RedisListEntry) redisListParser.getRedisEntry(0, parsedOdpfMessage, schema).get(0);
        RedisListEntry expectedEntry = new RedisListEntry("test-key", "ORDER-1-FROM-KEY", null, 0);
        assertEquals(expectedEntry, redisListEntry);
    }
    @Test
    public void shouldThrowExceptionForEmptyKey() throws IOException {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Template '' is invalid");

        setRedisSinkConfig(SinkConnectorSchemaMessageMode.LOG_MESSAGE, "");
        SinkConnectorSchemaMessageMode mode = redisSinkConfig.getSinkConnectorSchemaMessageMode();
        String schemaClass = "io.odpf.depot.TestMessage";
        ProtoOdpfMessageParser odpfMessageParser = new ProtoOdpfMessageParser(redisSinkConfig, statsDReporter, null);
        RedisParser redisListParser = new RedisListEntryParser(odpfMessageParser, redisSinkConfig, statsDReporter);
        ParsedOdpfMessage parsedOdpfMessage = odpfMessageParser.parse(message, mode, schemaClass);
        OdpfMessageSchema schema = odpfMessageParser.getSchema(schemaClass, descriptorsMap);
        redisListParser.getRedisEntry(0, parsedOdpfMessage, schema).get(0);
    }

    @Test
    public void shouldThrowExceptionForNoListProtoFieldName() throws IOException {
        setRedisSinkConfig(SinkConnectorSchemaMessageMode.LOG_MESSAGE, "test-key");
        when(redisSinkConfig.getSinkRedisListDataFieldName()).thenReturn(null);
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Empty config SINK_REDIS_LIST_DATA_FIELD_NAME found");
        SinkConnectorSchemaMessageMode mode = redisSinkConfig.getSinkConnectorSchemaMessageMode();
        String schemaClass = "io.odpf.depot.TestMessage";
        ProtoOdpfMessageParser odpfMessageParser = new ProtoOdpfMessageParser(redisSinkConfig, statsDReporter, null);
        RedisParser redisListParser = new RedisListEntryParser(odpfMessageParser, redisSinkConfig, statsDReporter);
        ParsedOdpfMessage parsedOdpfMessage = odpfMessageParser.parse(message, mode, schemaClass);
        OdpfMessageSchema schema = odpfMessageParser.getSchema(schemaClass, descriptorsMap);
        redisListParser.getRedisEntry(0, parsedOdpfMessage, schema).get(0);
    }
}
