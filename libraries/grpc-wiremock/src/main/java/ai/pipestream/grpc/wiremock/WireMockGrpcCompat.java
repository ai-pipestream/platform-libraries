package ai.pipestream.grpc.wiremock;

import com.google.protobuf.MessageOrBuilder;
import org.wiremock.grpc.dsl.GrpcResponseDefinitionBuilder;
import org.wiremock.grpc.dsl.GrpcStubMappingBuilder;

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
     *
     * @param messageOrBuilder A built message or builder representing the expected request
     * @return a StringValuePattern that matches the request JSON
     */
    public static com.github.tomakehurst.wiremock.matching.StringValuePattern equalToMessage(MessageOrBuilder messageOrBuilder) {
        final String json = ProtoJson.toJson(messageOrBuilder);
        return com.github.tomakehurst.wiremock.client.WireMock.equalToJson(json, true, false);
    }
}
