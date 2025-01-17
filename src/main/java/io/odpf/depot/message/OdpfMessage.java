package io.odpf.depot.message;

import io.odpf.depot.common.Tuple;
import io.odpf.depot.common.TupleString;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Getter
@EqualsAndHashCode
public class OdpfMessage {
    private final Object logKey;
    private final Object logMessage;
    private final Map<String, Object> metadata = new HashMap<>();

    public String getMetadataString() {
        return metadata.keySet().stream()
                .map(key -> key + "=" + metadata.get(key))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    @SafeVarargs
    public OdpfMessage(Object logKey, Object logMessage, Tuple<String, Object>... tuples) {
        this.logKey = logKey;
        this.logMessage = logMessage;
        Arrays.stream(tuples).forEach(t -> metadata.put(t.getFirst(), t.getSecond()));
    }

    public Map<String, Object> getMetadata(List<TupleString> metadataColumnsTypes) {
        return metadataColumnsTypes.stream()
                .collect(Collectors.toMap(
                        TupleString::getFirst, columnAndType -> metadata.get(columnAndType.getFirst())));
    }
}
