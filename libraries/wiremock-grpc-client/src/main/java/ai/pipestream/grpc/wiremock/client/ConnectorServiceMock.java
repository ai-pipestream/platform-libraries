package ai.pipestream.grpc.wiremock.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.protobuf.Timestamp;
import ai.pipestream.connector.intake.ConnectorRegistration;
import ai.pipestream.connector.intake.ValidateApiKeyRequest;
import ai.pipestream.connector.intake.ValidateApiKeyResponse;

import static ai.pipestream.grpc.wiremock.client.WireMockGrpcClient.*;

/**
 * Ready-to-use mock utilities for the Connector Service (ConnectorAdminService).
 * Uses standard gRPC mocking that works with both standard and Mutiny clients.
 */
@SuppressWarnings("UnusedReturnValue")
public class ConnectorServiceMock {

    private static final String SERVICE_NAME = "ai.pipestream.connector.intake.ConnectorAdminService";

    /**
     * Creates a connector service mock.
     * Note: In this client-only version, we don't hold a service instance.
     * The stubs are registered globally to the WireMock server connected via WireMock.configureFor().
     */
    public ConnectorServiceMock() {
        // No-op constructor
    }

    /**
     * Mock successful API key validation.
     */
    public ConnectorServiceMock mockValidateApiKey(String connectorId, String apiKey, String accountId) {
        WireMock.stubFor(
            grpcStubFor(SERVICE_NAME, "ValidateApiKey")
                .withRequestBody(equalToGrpcMessage(
                    ValidateApiKeyRequest.newBuilder()
                        .setConnectorId(connectorId)
                        .setApiKey(apiKey)
                        .build()
                ))
                .willReturn(aGrpcResponseWith(
                    ValidateApiKeyResponse.newBuilder()
                        .setValid(true)
                        .setMessage("API key validated successfully")
                        .setConnector(ConnectorRegistration.newBuilder()
                            .setConnectorId(connectorId)
                            .setConnectorName("Test Connector")
                            .setConnectorType("filesystem")
                            .setAccountId(accountId)
                            .setApiKey(apiKey)
                            .setS3Bucket("test-bucket")
                            .setS3BasePath("test/path/")
                            .setMaxFileSize(1024 * 1024 * 1024) // 1GB
                            .setRateLimitPerMinute(1000)
                            .setActive(true)
                            .setCreated(Timestamp.newBuilder()
                                .setSeconds(System.currentTimeMillis() / 1000)
                                .setNanos(0)
                                .build())
                            .setUpdated(Timestamp.newBuilder()
                                .setSeconds(System.currentTimeMillis() / 1000)
                                .setNanos(0)
                                .build())
                            .build())
                        .build()
                ))
        );
        
        return this;
    }

    /**
     * Mock successful API key validation with custom connector registration.
     */
    public ConnectorServiceMock mockValidateApiKey(String connectorId, String apiKey, ConnectorRegistration connector) {
        WireMock.stubFor(
            grpcStubFor(SERVICE_NAME, "ValidateApiKey")
                .withRequestBody(equalToGrpcMessage(
                    ValidateApiKeyRequest.newBuilder()
                        .setConnectorId(connectorId)
                        .setApiKey(apiKey)
                        .build()
                ))
                .willReturn(aGrpcResponseWith(
                    ValidateApiKeyResponse.newBuilder()
                        .setValid(true)
                        .setMessage("API key validated successfully")
                        .setConnector(connector)
                        .build()
                ))
        );
        
        return this;
    }

    /**
     * Mock failed API key validation (returns valid=false in response).
     */
    public ConnectorServiceMock mockValidateApiKeyFailed(String connectorId, String apiKey, String errorMessage) {
        WireMock.stubFor(
            grpcStubFor(SERVICE_NAME, "ValidateApiKey")
                .withRequestBody(equalToGrpcMessage(
                    ValidateApiKeyRequest.newBuilder()
                        .setConnectorId(connectorId)
                        .setApiKey(apiKey)
                        .build()
                ))
                .willReturn(aGrpcResponseWith(
                    ValidateApiKeyResponse.newBuilder()
                        .setValid(false)
                        .setMessage(errorMessage)
                        .build()
                ))
        );
        
        return this;
    }

    /**
     * Mock API key validation failure with NOT_FOUND status (connector doesn't exist).
     */
    public ConnectorServiceMock mockValidateApiKeyNotFound(String connectorId, String apiKey) {
        WireMock.stubFor(
            grpcStubFor(SERVICE_NAME, "ValidateApiKey")
                .withRequestBody(equalToGrpcMessage(
                    ValidateApiKeyRequest.newBuilder()
                        .setConnectorId(connectorId)
                        .setApiKey(apiKey)
                        .build()
                ))
                .willReturn(aGrpcErrorResponse(
                    io.grpc.Status.Code.NOT_FOUND.value(),
                    "Connector not found: " + connectorId))
        );
        return this;
    }

    /**
     * Mock API key validation failure with PERMISSION_DENIED status (connector inactive or account inactive).
     */
    public ConnectorServiceMock mockValidateApiKeyPermissionDenied(String connectorId, String apiKey, String errorMessage) {
        WireMock.stubFor(
            grpcStubFor(SERVICE_NAME, "ValidateApiKey")
                .withRequestBody(equalToGrpcMessage(
                    ValidateApiKeyRequest.newBuilder()
                        .setConnectorId(connectorId)
                        .setApiKey(apiKey)
                        .build()
                ))
                .willReturn(aGrpcErrorResponse(
                    io.grpc.Status.Code.PERMISSION_DENIED.value(),
                    errorMessage))
        );
        return this;
    }

    /**
     * Mock API key validation failure with RESOURCE_EXHAUSTED status (rate limit exceeded).
     */
    public ConnectorServiceMock mockValidateApiKeyRateLimited(String connectorId, String apiKey) {
        WireMock.stubFor(
            grpcStubFor(SERVICE_NAME, "ValidateApiKey")
                .withRequestBody(equalToGrpcMessage(
                    ValidateApiKeyRequest.newBuilder()
                        .setConnectorId(connectorId)
                        .setApiKey(apiKey)
                        .build()
                ))
                .willReturn(aGrpcErrorResponse(
                    io.grpc.Status.Code.RESOURCE_EXHAUSTED.value(),
                    "Rate limit exceeded for connector: " + connectorId))
        );
        return this;
    }

    /**
     * Mock API key validation failure with UNAUTHENTICATED status (invalid API key).
     */
    public ConnectorServiceMock mockValidateApiKeyUnauthenticated(String connectorId, String apiKey) {
        WireMock.stubFor(
            grpcStubFor(SERVICE_NAME, "ValidateApiKey")
                .withRequestBody(equalToGrpcMessage(
                    ValidateApiKeyRequest.newBuilder()
                        .setConnectorId(connectorId)
                        .setApiKey(apiKey)
                        .build()
                ))
                .willReturn(aGrpcErrorResponse(
                    io.grpc.Status.Code.UNAUTHENTICATED.value(),
                    "Invalid API key for connector: " + connectorId))
        );
        return this;
    }
}
