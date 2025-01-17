package io.odpf.depot.message;

import java.io.IOException;

public interface OdpfMessageParser {
    ParsedOdpfMessage parse(OdpfMessage message, SinkConnectorSchemaMessageMode type, String schemaClass) throws IOException;

    OdpfMessageSchema getSchema(String schemaClass) throws IOException;
}
