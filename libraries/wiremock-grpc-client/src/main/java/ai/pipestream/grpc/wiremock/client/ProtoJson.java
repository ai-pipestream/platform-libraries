package ai.pipestream.grpc.wiremock.client;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;

import java.io.IOException;

/**
 * Utility class for converting Protobuf messages to JSON strings.
 * This class uses the project's standard Protobuf library (4.x) and is intended
 * for client-side JSON serialization when interacting with the external WireMock server.
 * <p>
 * This replaces the previous ProtoJson from the embedded grpc-wiremock, ensuring
 * compatibility with the project's Protobuf version.
 */
public final class ProtoJson {

    private static final JsonFormat.Printer PRINTER_WITH_DEFAULTS = JsonFormat.printer()
            .includingDefaultValueFields()
            .omittingInsignificantWhitespace();

    private static final JsonFormat.Printer PRINTER_WITHOUT_DEFAULTS = JsonFormat.printer()
            .omittingInsignificantWhitespace();


    private ProtoJson() {
        // Private constructor for utility class
    }

    /**
     * Converts a Protobuf message or builder to its JSON string representation,
     * including fields with default values.
     *
     * @param messageOrBuilder The Protobuf message or builder.
     * @return The JSON string.
     */
    public static String toJson(MessageOrBuilder messageOrBuilder) {
        try {
            return PRINTER_WITH_DEFAULTS.print(messageOrBuilder);
        } catch (IOException e) {
            throw new RuntimeException("Failed to convert Protobuf message to JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Converts a Protobuf message or builder to its JSON string representation,
     * omitting fields with default values. This is typically used for request matching,
     * as gRPC clients often omit default values when sending requests.
     *
     * @param messageOrBuilder The Protobuf message or builder.
     * @return The JSON string.
     */
    public static String toJsonWithoutDefaults(MessageOrBuilder messageOrBuilder) {
        try {
            return PRINTER_WITHOUT_DEFAULTS.print(messageOrBuilder);
        } catch (IOException e) {
            throw new RuntimeException("Failed to convert Protobuf message to JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Parses a JSON string into a Protobuf message builder.
     *
     * @param jsonString The JSON string.
     * @param builder    The Protobuf message builder to parse into.
     * @param <T>        The type of the builder.
     * @return The builder with the parsed JSON.
     */
    public static <T extends Message.Builder> T fromJson(String jsonString, T builder) {
        try {
            JsonFormat.parser().ignoringUnknownFields().merge(jsonString, builder);
            return builder;
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse JSON into Protobuf message: " + e.getMessage(), e);
        }
    }
}
