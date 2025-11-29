package ai.pipestream.grpc.wiremock.client;

import com.google.protobuf.MessageOrBuilder;
import org.wiremock.grpc.dsl.GrpcResponseDefinitionBuilder;
import org.wiremock.grpc.dsl.GrpcStubMappingBuilder;
import ai.pipestream.grpc.wiremock.client.ProtoJson;

/**
 * Compatibility wrapper around WireMock gRPC DSL for use with the standalone artifact.
 * The standalone artifact shades protobuf into wiremock.com.google.protobuf, so
 * org.wiremock.grpc.dsl.WireMockGrpc.message(MessageOrBuilder) becomes incompatible
 * with project proto classes. This shim exposes a message(MessageOrBuilder) that
 * converts to JSON and delegates to WireMockGrpc.json(String).
 */
public final class WireMockGrpcCompat {
    private WireMockGrpcCompat() {}

    /**
     * Begin a gRPC stub definition for the given method name.
     *
     * @param method The gRPC method name as defined in the .proto (case-sensitive)
     * @return a GrpcStubMappingBuilder to continue request/response configuration
     */
    public static GrpcStubMappingBuilder method(String method) {
        return org.wiremock.grpc.dsl.WireMockGrpc.method(method);
    }

    /**
     * Define a gRPC OK response from a JSON string.
     * Typically used with jsonTemplate for dynamic responses; safe with standalone artifacts.
     *
     * @param json The JSON representation of the response message
     * @return a GrpcResponseDefinitionBuilder to complete the stub
     */
    public static GrpcResponseDefinitionBuilder json(String json) {
        return org.wiremock.grpc.dsl.WireMockGrpc.json(json);
    }

    /**
     * Define a gRPC OK response using the Handlebars templating engine.
     * This allows you to reference request data via {{jsonPath ...}} expressions.
     *
     * @param json The JSON template for the response message
     * @return a GrpcResponseDefinitionBuilder to complete the stub
     */
    public static GrpcResponseDefinitionBuilder jsonTemplate(String json) {
        return org.wiremock.grpc.dsl.WireMockGrpc.jsonTemplate(json);
    }

    /**
     * Define a gRPC OK response using a protobuf message or builder from your project.
     * Internally converts the message to JSON to avoid shaded protobuf incompatibilities.
     *
     * @param messageOrBuilder A built message or builder from com.google.protobuf
     * @return a GrpcResponseDefinitionBuilder to complete the stub
     */
    public static GrpcResponseDefinitionBuilder message(MessageOrBuilder messageOrBuilder) {
        // Convert the unshaded protobuf (message or builder) to JSON and use the JSON-based response builder
        return org.wiremock.grpc.dsl.WireMockGrpc.json(ProtoJson.toJson(messageOrBuilder));
    }

    /**
     * Request body matcher equivalent to WireMockGrpc.equalToMessage(MessageOrBuilder),
     * implemented via JSON to avoid shaded protobuf type incompatibilities.
     * <p>
     * Uses {@link ProtoJson#toJsonWithoutDefaults(MessageOrBuilder)} to ensure
     * the JSON matches what gRPC clients actually send (which may omit default values).
     * <p>
     * Uses {@code equalToJson} with {@code ignoreExtraElements=true} to allow the actual
     * request to contain additional fields beyond those in the expected message.
     * <p>
     * <b>Known Limitation:</b> WireMock's {@code equalToJson} with {@code ignoreExtraElements=true}
     * can be unreliable when multiple stubs exist for the same method (see
     * <a href="https://github.com/wiremock/wiremock/issues/1230">wiremock/wiremock#1230</a>).
     * When possible, build your expected request with all fields that will be present in the
     * actual request to ensure more reliable matching.
     * <p>
     * <b>Best Practice:</b> When matching on specific fields (e.g., nodeId and chunkNumber),
     * ensure your expected request includes ALL fields that must match exactly. WireMock evaluates
     * stubs and matches the first one where the expected JSON is a subset of the actual JSON.
     *
     * @param messageOrBuilder A built message or builder representing the expected request
     * @return a StringValuePattern that matches the request JSON
     */
    public static com.github.tomakehurst.wiremock.matching.StringValuePattern equalToMessage(MessageOrBuilder messageOrBuilder) {
        // Use toJsonWithoutDefaults for request matching to match what gRPC actually sends
        final String json = ProtoJson.toJsonWithoutDefaults(messageOrBuilder);
        // Use ignoreExtraElements=true to allow actual requests to have additional fields
        // (e.g., uploadId, data in UploadChunkRequest when we only match on nodeId and chunkNumber)
        // Note: This can be unreliable with multiple stubs due to WireMock bug #1230
        return com.github.tomakehurst.wiremock.client.WireMock.equalToJson(json, true, false);
    }

    /**
     * Create an exact match matcher for a protobuf message.
     * <p>
     * This is more precise than {@link #equalToMessage(MessageOrBuilder)} - it requires
     * an exact match of all fields, with no extra elements allowed.
     * <p>
     * Use this when you want to match on the complete request structure, ensuring
     * predictable behavior when multiple stubs exist for the same method.
     *
     * @param messageOrBuilder A built message or builder representing the expected request
     * @return a StringValuePattern that matches the request JSON exactly
     */
    public static com.github.tomakehurst.wiremock.matching.StringValuePattern equalToMessageExact(MessageOrBuilder messageOrBuilder) {
        // Use toJsonWithoutDefaults for request matching to match what gRPC actually sends
        final String json = ProtoJson.toJsonWithoutDefaults(messageOrBuilder);
        // Use ignoreExtraElements=false for exact matching
        return com.github.tomakehurst.wiremock.client.WireMock.equalToJson(json, false, false);
    }
}