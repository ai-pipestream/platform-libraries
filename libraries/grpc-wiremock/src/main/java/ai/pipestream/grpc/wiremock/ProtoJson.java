package ai.pipestream.grpc.wiremock;

import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;

/**
 * Utility to convert project protobuf messages to JSON strings for use with
 * WireMock gRPC standalone DSL methods (json/jsonTemplate), avoiding shaded
 * protobuf type conflicts.
 */
public final class ProtoJson {
    private static final JsonFormat.Printer PRINTER = JsonFormat.printer()
            .includingDefaultValueFields()
            .omittingInsignificantWhitespace();

    private ProtoJson() {}

    /**
     * Serialize a protobuf message or builder to its compact JSON representation.
     * Includes default-valued fields and omits insignificant whitespace.
     *
     * @param messageOrBuilder the message or builder to serialize
     * @return compact JSON string
     * @throws RuntimeException if serialization fails
     */
    public static String toJson(MessageOrBuilder messageOrBuilder) {
        try {
            return PRINTER.print(messageOrBuilder);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert protobuf message to JSON", e);
        }
    }
}
