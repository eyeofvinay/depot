package io.odpf.depot.bigquery.models;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.LegacySQLTypeName;
import com.google.protobuf.DescriptorProtos;
import io.odpf.depot.message.proto.Constants;
import io.odpf.depot.message.proto.ProtoField;
import io.odpf.depot.bigquery.exception.BQSchemaMappingException;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode
public class BQField {
    private static final Map<DescriptorProtos.FieldDescriptorProto.Label, Field.Mode> FIELD_LABEL_TO_BQ_MODE_MAP = new HashMap<DescriptorProtos.FieldDescriptorProto.Label, Field.Mode>() {{
        put(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL, Field.Mode.NULLABLE);
        put(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED, Field.Mode.REPEATED);
        put(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REQUIRED, Field.Mode.REQUIRED);
    }};
    private static final Map<DescriptorProtos.FieldDescriptorProto.Type, LegacySQLTypeName> FIELD_TYPE_TO_BQ_TYPE_MAP = new HashMap<DescriptorProtos.FieldDescriptorProto.Type, LegacySQLTypeName>() {{
        put(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES, LegacySQLTypeName.BYTES);
        put(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING, LegacySQLTypeName.STRING);
        put(DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM, LegacySQLTypeName.STRING);
        put(DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE, LegacySQLTypeName.FLOAT);
        put(DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT, LegacySQLTypeName.FLOAT);
        put(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL, LegacySQLTypeName.BOOLEAN);
        put(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64, LegacySQLTypeName.INTEGER);
        put(DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT64, LegacySQLTypeName.INTEGER);
        put(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32, LegacySQLTypeName.INTEGER);
        put(DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT32, LegacySQLTypeName.INTEGER);
        put(DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED64, LegacySQLTypeName.INTEGER);
        put(DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED32, LegacySQLTypeName.INTEGER);
        put(DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED32, LegacySQLTypeName.INTEGER);
        put(DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED64, LegacySQLTypeName.INTEGER);
        put(DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT32, LegacySQLTypeName.INTEGER);
        put(DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64, LegacySQLTypeName.INTEGER);
        put(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE, LegacySQLTypeName.RECORD);
        put(DescriptorProtos.FieldDescriptorProto.Type.TYPE_GROUP, LegacySQLTypeName.RECORD);
    }};
    private static final Map<String, LegacySQLTypeName> FIELD_NAME_TO_BQ_TYPE_MAP = new HashMap<String, LegacySQLTypeName>() {{
        put(Constants.ProtobufTypeName.TIMESTAMP_PROTOBUF_TYPE_NAME, LegacySQLTypeName.TIMESTAMP);
        put(Constants.ProtobufTypeName.STRUCT_PROTOBUF_TYPE_NAME, LegacySQLTypeName.STRING);
        put(Constants.ProtobufTypeName.DURATION_PROTOBUF_TYPE_NAME, LegacySQLTypeName.RECORD);
    }};
    private final String name;
    private final Field.Mode mode;
    private final LegacySQLTypeName type;
    private List<Field> subFields;

    public BQField(String name, Field.Mode mode, LegacySQLTypeName type, List<Field> subFields) {
        this.name = name;
        this.mode = mode;
        this.type = type;
        this.subFields = subFields;
    }

    public BQField(ProtoField protoField) {
        this.name = protoField.getName();
        this.mode = FIELD_LABEL_TO_BQ_MODE_MAP.get(protoField.getLabel());
        this.type = getType(protoField);
        this.subFields = new ArrayList<>();
    }

    /**
     * Map fully qualified type name or protobuf type to bigquery types.
     * Fully qualified name will be used as mapping key before protobuf type being used
     * @param protoField
     * @return
     */
    private LegacySQLTypeName getType(ProtoField protoField) {
        LegacySQLTypeName typeFromFieldName = FIELD_NAME_TO_BQ_TYPE_MAP.get(protoField.getTypeName()) != null
                ? FIELD_NAME_TO_BQ_TYPE_MAP.get(protoField.getTypeName())
                : FIELD_TYPE_TO_BQ_TYPE_MAP.get(protoField.getType());
        if (typeFromFieldName == null) {
            throw new BQSchemaMappingException(String.format("No type mapping found for field: %s, fieldType: %s, typeName: %s", protoField.getName(), protoField.getType(), protoField.getTypeName()));
        }
        return typeFromFieldName;
    }

    public void setSubFields(List<Field> fields) {
        this.subFields = fields;
    }

    public Field getField() {
        if (this.subFields == null || this.subFields.size() == 0) {
            return Field.newBuilder(this.name, this.type).setMode(this.mode).build();
        }
        return Field.newBuilder(this.name, this.type, FieldList.of(subFields)).setMode(this.mode).build();
    }

    public String getName() {
        return name;
    }

    public LegacySQLTypeName getType() {
        return type;
    }


}
