package ai.pipestream.grpc.wiremock;

import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;

/**
 * Utility to convert project protobuf messages to JSON strings for use with
 * WireMock gRPC standalone DSL methods (json/jsonTemplate), avoiding shaded
 * protobuf type conflicts.
 */
public final class ProtoJson {
    /**
     * Printer that includes default-valued fields.
     * Used for response serialization where we want complete JSON.
     */
    private static final JsonFormat.Printer PRINTER_WITH_DEFAULTS = JsonFormat.printer()
            .includingDefaultValueFields()
            .omittingInsignificantWhitespace();

    /**
     * Printer that excludes default-valued fields.
     * Used for request matching where gRPC clients may omit default values.
     */
    private static final JsonFormat.Printer PRINTER_WITHOUT_DEFAULTS = JsonFormat.printer()
            .omittingInsignificantWhitespace();

    private ProtoJson() {}

    /**
     * Serialize a protobuf message or builder to its compact JSON representation.
     * Includes default-valued fields and omits insignificant whitespace.
     * <p>
     * Use this for response serialization where you want complete JSON output.
     *
     * @param messageOrBuilder the message or builder to serialize
     * @return compact JSON string
     * @throws RuntimeException if serialization fails
     */
    public static String toJson(MessageOrBuilder messageOrBuilder) {
        try {
            return PRINTER_WITH_DEFAULTS.print(messageOrBuilder);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert protobuf message to JSON", e);
        }
    }

    /**
     * Serialize a protobuf message or builder to JSON without default-valued fields.
     * <p>
     * Use this for request matching where gRPC clients may omit default values.
     * This ensures the JSON matches what gRPC actually sends over the wire.
     *
     * @param messageOrBuilder the message or builder to serialize
     * @return compact JSON string without default values
     * @throws RuntimeException if serialization fails
     */
    public static String toJsonWithoutDefaults(MessageOrBuilder messageOrBuilder) {
        try {
            return PRINTER_WITHOUT_DEFAULTS.print(messageOrBuilder);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert protobuf message to JSON", e);
        }
    }
}
