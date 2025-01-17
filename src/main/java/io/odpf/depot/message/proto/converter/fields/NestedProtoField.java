package io.odpf.depot.message.proto.converter.fields;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class NestedProtoField implements ProtoField {
    private final Descriptors.FieldDescriptor descriptor;
    private final Object fieldValue;

    @Override
    public DynamicMessage getValue() {
        return (DynamicMessage) fieldValue;
    }

    @Override
    public boolean matches() {
        return descriptor.getType() == Descriptors.FieldDescriptor.Type.MESSAGE && !(fieldValue instanceof List);
    }
}
