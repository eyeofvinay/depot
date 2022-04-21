package io.odpf.sink.connectors.bigquery.proto;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.LegacySQLTypeName;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import io.odpf.sink.connectors.TestKeyBQ;
import io.odpf.sink.connectors.bigquery.converter.MessageRecordConverterCache;
import io.odpf.sink.connectors.bigquery.handler.BigQueryClient;
import io.odpf.sink.connectors.bigquery.models.MetadataUtil;
import io.odpf.sink.connectors.bigquery.models.ProtoField;
import io.odpf.sink.connectors.bigquery.models.Records;
import io.odpf.sink.connectors.config.BigQuerySinkConfig;
import io.odpf.sink.connectors.config.Tuple;
import io.odpf.sink.connectors.config.TupleString;
import io.odpf.sink.connectors.message.*;
import io.odpf.stencil.Parser;
import io.odpf.stencil.client.StencilClient;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ProtoUpdateListenerTest {
    @Mock
    private BigQueryClient bigQueryClient;
    @Mock
    private StencilClient stencilClient;

    private BigQuerySinkConfig config;

    private MessageRecordConverterCache converterWrapper;

    @Before
    public void setUp() throws InvalidProtocolBufferException {
        System.setProperty("INPUT_SCHEMA_PROTO_CLASS", "io.odpf.sink.connectors.TestKeyBQ");
        System.setProperty("SINK_BIGQUERY_ENABLE_AUTO_SCHEMA_UPDATE", "false");
        System.setProperty("SINK_BIGQUERY_METADATA_COLUMNS_TYPES", "topic=string,partition=integer,offset=integer");
        config = ConfigFactory.create(BigQuerySinkConfig.class, System.getProperties());
        converterWrapper = new MessageRecordConverterCache();
        when(stencilClient.parse(Mockito.anyString(), Mockito.any())).thenCallRealMethod();
    }

    @Test
    public void shouldUseNewSchemaIfProtoChanges() {
        ProtoUpdateListener protoUpdateListener = new ProtoUpdateListener(config, bigQueryClient, converterWrapper);

        ProtoField returnedProtoField = new ProtoField();
        returnedProtoField.addField(ProtoUtil.createProtoField("order_number", 1));
        returnedProtoField.addField(ProtoUtil.createProtoField("order_url", 2));

        HashMap<String, Descriptor> descriptorsMap = new HashMap<String, Descriptor>() {{
            put(String.format("%s", TestKeyBQ.class.getName()), TestKeyBQ.getDescriptor());
        }};
        when(stencilClient.get(TestKeyBQ.class.getName())).thenReturn(descriptorsMap.get(TestKeyBQ.class.getName()));
        ObjectNode objNode = JsonNodeFactory.instance.objectNode();
        objNode.put("1", "order_number");
        objNode.put("2", "order_url");

        ArrayList<Field> bqSchemaFields = new ArrayList<Field>() {{
            add(Field.newBuilder("order_number", LegacySQLTypeName.STRING).setMode(Field.Mode.NULLABLE).build());
            add(Field.newBuilder("order_url", LegacySQLTypeName.STRING).setMode(Field.Mode.NULLABLE).build());
            addAll(MetadataUtil.getMetadataFields(new ArrayList<TupleString>() {{
                add(new TupleString("topic", "string"));
                add(new TupleString("partition", "integer"));
                add(new TupleString("offset", "integer"));
            }}));
        }};
        doNothing().when(bigQueryClient).upsertTable(bqSchemaFields);

        OdpfMessageParser parser = new ProtoOdpfMessageParser(stencilClient);
        protoUpdateListener.setMessageParser(parser);
        protoUpdateListener.onSchemaUpdate(descriptorsMap);
        TestKeyBQ testKeyBQ = TestKeyBQ.newBuilder().setOrderNumber("order").setOrderUrl("test").build();
        OdpfMessage testMessage = new ProtoOdpfMessage(
                "".getBytes(),
                testKeyBQ.toByteArray(),
                new Tuple<>("topic", "topic"),
                new Tuple<>("partition", 1),
                new Tuple<>("offset", 1));
        Records convert = protoUpdateListener.getMessageRecordConverterCache().getMessageRecordConverter().convert(Collections.singletonList(testMessage));
        Assert.assertEquals(1, convert.getValidRecords().size());
        Assert.assertEquals("order", convert.getValidRecords().get(0).getColumns().get("order_number"));
        Assert.assertEquals("test", convert.getValidRecords().get(0).getColumns().get("order_url"));
    }


    @Test(expected = RuntimeException.class)
    public void shouldThrowExceptionIfParserFails() {
        ProtoUpdateListener protoUpdateListener = new ProtoUpdateListener(config, bigQueryClient, converterWrapper);

        HashMap<String, Descriptor> descriptorsMap = new HashMap<String, Descriptor>() {{
            put(String.format("%s", TestKeyBQ.class.getName()), null);
        }};
        ObjectNode objNode = JsonNodeFactory.instance.objectNode();
        objNode.put("1", "order_number");
        objNode.put("2", "order_url");

        protoUpdateListener.onSchemaUpdate(descriptorsMap);
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowExceptionIfConverterFails() {
        ProtoUpdateListener protoUpdateListener = new ProtoUpdateListener(config, bigQueryClient, converterWrapper);
        ProtoField returnedProtoField = new ProtoField();
        returnedProtoField.addField(ProtoUtil.createProtoField("order_number", 1));
        returnedProtoField.addField(ProtoUtil.createProtoField("order_url", 2));

        HashMap<String, Descriptor> descriptorsMap = new HashMap<String, Descriptor>() {{
            put(String.format("%s", TestKeyBQ.class.getName()), TestKeyBQ.getDescriptor());
        }};
        ObjectNode objNode = JsonNodeFactory.instance.objectNode();
        objNode.put("1", "order_number");
        objNode.put("2", "order_url");

        doThrow(new BigQueryException(10, "bigquery mapping has failed")).when(bigQueryClient).upsertTable(any());

        protoUpdateListener.onSchemaUpdate(descriptorsMap);
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowExceptionIfDatasetLocationIsChanged() throws IOException {
        ProtoUpdateListener protoUpdateListener = new ProtoUpdateListener(config, bigQueryClient, converterWrapper);

        ProtoField returnedProtoField = new ProtoField();
        returnedProtoField.addField(ProtoUtil.createProtoField("order_number", 1));
        returnedProtoField.addField(ProtoUtil.createProtoField("order_url", 2));

        HashMap<String, Descriptor> descriptorsMap = new HashMap<String, Descriptor>() {{
            put(String.format("%s", TestKeyBQ.class.getName()), TestKeyBQ.getDescriptor());
        }};
        ObjectNode objNode = JsonNodeFactory.instance.objectNode();
        objNode.put("1", "order_number");
        objNode.put("2", "order_url");
        doThrow(new RuntimeException("cannot change dataset location")).when(bigQueryClient).upsertTable(any());

        protoUpdateListener.onSchemaUpdate(descriptorsMap);
    }

    @Test
    public void shouldNotNamespaceMetadataFieldsWhenNamespaceIsNotProvided() throws IOException {
        ProtoUpdateListener protoUpdateListener = new ProtoUpdateListener(config, bigQueryClient, converterWrapper);

        ProtoField returnedProtoField = new ProtoField();
        returnedProtoField.addField(ProtoUtil.createProtoField("order_number", 1));
        returnedProtoField.addField(ProtoUtil.createProtoField("order_url", 2));

        HashMap<String, Descriptor> descriptorsMap = new HashMap<String, Descriptor>() {{
            put(String.format("%s", TestKeyBQ.class.getName()), TestKeyBQ.getDescriptor());
        }};
        when(stencilClient.get(TestKeyBQ.class.getName())).thenReturn(descriptorsMap.get(TestKeyBQ.class.getName()));
        ObjectNode objNode = JsonNodeFactory.instance.objectNode();
        objNode.put("1", "order_number");
        objNode.put("2", "order_url");

        ArrayList<Field> bqSchemaFields = new ArrayList<Field>() {{
            add(Field.newBuilder("order_number", LegacySQLTypeName.STRING).setMode(Field.Mode.NULLABLE).build());
            add(Field.newBuilder("order_url", LegacySQLTypeName.STRING).setMode(Field.Mode.NULLABLE).build());
            addAll(MetadataUtil.getMetadataFields(new ArrayList<TupleString>() {{
                add(new TupleString("topic", "string"));
                add(new TupleString("partition", "integer"));
                add(new TupleString("offset", "integer"));
            }}));
        }};
        doNothing().when(bigQueryClient).upsertTable(bqSchemaFields);
        OdpfMessageParser parser = new ProtoOdpfMessageParser(stencilClient);
        protoUpdateListener.setMessageParser(parser);
        protoUpdateListener.onSchemaUpdate(descriptorsMap);
        TestKeyBQ testKeyBQ = TestKeyBQ.newBuilder().setOrderNumber("order").setOrderUrl("test").build();
        OdpfMessage testMessage = new ProtoOdpfMessage(
                "".getBytes(),
                testKeyBQ.toByteArray(),
                new Tuple<>("topic", "topic"),
                new Tuple<>("partition", 1),
                new Tuple<>("offset", 1));
        Records convert = protoUpdateListener.getMessageRecordConverterCache().getMessageRecordConverter().convert(Collections.singletonList(testMessage));
        Assert.assertEquals(1, convert.getValidRecords().size());
        Assert.assertEquals("order", convert.getValidRecords().get(0).getColumns().get("order_number"));
        Assert.assertEquals("test", convert.getValidRecords().get(0).getColumns().get("order_url"));
        verify(bigQueryClient, times(1)).upsertTable(bqSchemaFields); // assert that metadata fields were not namespaced
    }

    @Test
    public void shouldNamespaceMetadataFieldsWhenNamespaceIsProvided() throws IOException {
        System.setProperty("SINK_BIGQUERY_METADATA_NAMESPACE", "metadata_ns");
        config = ConfigFactory.create(BigQuerySinkConfig.class, System.getProperties());
        ProtoUpdateListener protoUpdateListener = new ProtoUpdateListener(config, bigQueryClient, converterWrapper);

        ProtoField returnedProtoField = new ProtoField();
        returnedProtoField.addField(ProtoUtil.createProtoField("order_number", 1));
        returnedProtoField.addField(ProtoUtil.createProtoField("order_url", 2));

        HashMap<String, Descriptor> descriptorsMap = new HashMap<String, Descriptor>() {{
            put(String.format("%s", TestKeyBQ.class.getName()), TestKeyBQ.getDescriptor());
        }};
        when(stencilClient.get(TestKeyBQ.class.getName())).thenReturn(descriptorsMap.get(TestKeyBQ.class.getName()));
        ObjectNode objNode = JsonNodeFactory.instance.objectNode();
        objNode.put("1", "order_number");
        objNode.put("2", "order_url");

        ArrayList<Field> bqSchemaFields = new ArrayList<Field>() {{
            add(Field.newBuilder("order_number", LegacySQLTypeName.STRING).setMode(Field.Mode.NULLABLE).build());
            add(Field.newBuilder("order_url", LegacySQLTypeName.STRING).setMode(Field.Mode.NULLABLE).build());
            add(MetadataUtil.getNamespacedMetadataField(config.getBqMetadataNamespace(), new ArrayList<TupleString>() {{
                add(new TupleString("topic", "string"));
                add(new TupleString("partition", "integer"));
                add(new TupleString("offset", "integer"));
            }}));
        }};
        doNothing().when(bigQueryClient).upsertTable(bqSchemaFields);

        OdpfMessageParser parser = new ProtoOdpfMessageParser(stencilClient);
        protoUpdateListener.setMessageParser(parser);
        protoUpdateListener.onSchemaUpdate(descriptorsMap);
        TestKeyBQ testKeyBQ = TestKeyBQ.newBuilder().setOrderNumber("order").setOrderUrl("test").build();
        OdpfMessage testMessage = new ProtoOdpfMessage(
                "".getBytes(),
                testKeyBQ.toByteArray(),
                new Tuple<>("topic", "topic"),
                new Tuple<>("partition", 1),
                new Tuple<>("offset", 1));
        Records convert = protoUpdateListener.getMessageRecordConverterCache().getMessageRecordConverter().convert(Collections.singletonList(testMessage));
        Assert.assertEquals(1, convert.getValidRecords().size());
        Assert.assertEquals("order", convert.getValidRecords().get(0).getColumns().get("order_number"));
        Assert.assertEquals("test", convert.getValidRecords().get(0).getColumns().get("order_url"));

        verify(bigQueryClient, times(1)).upsertTable(bqSchemaFields);
        System.setProperty("SINK_BIGQUERY_METADATA_NAMESPACE", "");
    }

    @Test
    public void shouldThrowExceptionWhenMetadataNamespaceNameCollidesWithAnyFieldName() throws IOException {
        System.setProperty("SINK_BIGQUERY_METADATA_NAMESPACE", "order_number"); // set field name to an existing column name
        config = ConfigFactory.create(BigQuerySinkConfig.class, System.getProperties());
        ProtoUpdateListener protoUpdateListener = new ProtoUpdateListener(config, bigQueryClient, converterWrapper);

        ProtoField returnedProtoField = new ProtoField();
        returnedProtoField.addField(ProtoUtil.createProtoField("order_number", 1));
        returnedProtoField.addField(ProtoUtil.createProtoField("order_url", 2));

        HashMap<String, Descriptor> descriptorsMap = new HashMap<String, Descriptor>() {{
            put(String.format("%s", TestKeyBQ.class.getName()), TestKeyBQ.getDescriptor());
        }};
        ObjectNode objNode = JsonNodeFactory.instance.objectNode();
        objNode.put("1", "order_number");
        objNode.put("2", "order_url");

        ArrayList<Field> bqSchemaFields = new ArrayList<Field>() {{
            add(Field.newBuilder("order_number", LegacySQLTypeName.STRING).setMode(Field.Mode.NULLABLE).build());
            add(Field.newBuilder("order_url", LegacySQLTypeName.STRING).setMode(Field.Mode.NULLABLE).build());
            add(MetadataUtil.getNamespacedMetadataField(config.getBqMetadataNamespace(), new ArrayList<TupleString>() {{
                add(new TupleString("topic", "string"));
                add(new TupleString("partition", "integer"));
                add(new TupleString("offset", "integer"));
            }}));
        }};

        Exception exception = Assertions.assertThrows(RuntimeException.class, () -> {
            protoUpdateListener.onSchemaUpdate(descriptorsMap);
        });
        Assert.assertEquals("Metadata field(s) is already present in the schema. fields: [order_number]", exception.getMessage());
        verify(bigQueryClient, times(0)).upsertTable(bqSchemaFields);
        System.setProperty("SINK_BIGQUERY_METADATA_NAMESPACE", "");
    }

}
