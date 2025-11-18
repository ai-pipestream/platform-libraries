package ai.pipestream.grpc.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import ai.pipestream.connector.intake.ConnectorRegistration;
import ai.pipestream.connector.intake.ConnectorAdminServiceGrpc;
import ai.pipestream.connector.intake.ValidateApiKeyRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConnectorServiceMock to verify the mock stubs work correctly.
 * <p>
 * This validates the mock framework before using it in other services.
 */
public class ConnectorServiceMockTest {

    private WireMockServer wireMockServer;
    private ManagedChannel channel;
    private ConnectorAdminServiceGrpc.ConnectorAdminServiceBlockingStub connectorService;
    private ConnectorServiceMock connectorServiceMock;

    @BeforeEach
    void setUp() {
        // Start WireMock with gRPC extension
        wireMockServer = new WireMockServer(
            com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig()
                .dynamicPort()
                .usingFilesUnderClasspath("META-INF")
                .extensions(new org.wiremock.grpc.GrpcExtensionFactory())
        );
        wireMockServer.start();

        // Create ConnectorServiceMock
        connectorServiceMock = new ConnectorServiceMock(wireMockServer.port());

        // Create gRPC client
        channel = ManagedChannelBuilder.forAddress("localhost", wireMockServer.port())
            .usePlaintext()
            .build();
        connectorService = ConnectorAdminServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdown();
        }
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void testMockValidateApiKey_Success() {
        // Setup mock
        connectorServiceMock.mockValidateApiKey("conn-123", "api-key-456", "account-789");

        // Call
        var response = connectorService.validateApiKey(
            ValidateApiKeyRequest.newBuilder()
                .setConnectorId("conn-123")
                .setApiKey("api-key-456")
                .build()
        );

        // Verify
        assertTrue(response.getValid());
        assertEquals("API key validated successfully", response.getMessage());
        assertTrue(response.hasConnector());
        
        ConnectorRegistration connector = response.getConnector();
        assertEquals("conn-123", connector.getConnectorId());
        assertEquals("Test Connector", connector.getConnectorName());
        assertEquals("filesystem", connector.getConnectorType());
        assertEquals("account-789", connector.getAccountId());
        assertEquals("api-key-456", connector.getApiKey());
        assertTrue(connector.getActive());
    }

    @Test
    void testMockValidateApiKey_WithCustomConnector() {
        // Setup mock with custom connector
        ConnectorRegistration customConnector = ConnectorRegistration.newBuilder()
            .setConnectorId("custom-conn")
            .setConnectorName("Custom Connector")
            .setConnectorType("s3")
            .setAccountId("custom-account")
            .setApiKey("custom-key")
            .setActive(true)
            .build();

        connectorServiceMock.mockValidateApiKey("custom-conn", "custom-key", customConnector);

        // Call
        var response = connectorService.validateApiKey(
            ValidateApiKeyRequest.newBuilder()
                .setConnectorId("custom-conn")
                .setApiKey("custom-key")
                .build()
        );

        // Verify
        assertTrue(response.getValid());
        ConnectorRegistration connector = response.getConnector();
        assertEquals("custom-conn", connector.getConnectorId());
        assertEquals("Custom Connector", connector.getConnectorName());
        assertEquals("s3", connector.getConnectorType());
    }

    @Test
    void testMockValidateApiKey_Failed() {
        // Setup mock for failed validation (returns valid=false)
        connectorServiceMock.mockValidateApiKeyFailed("conn-123", "wrong-key", "Invalid API key");

        // Call
        var response = connectorService.validateApiKey(
            ValidateApiKeyRequest.newBuilder()
                .setConnectorId("conn-123")
                .setApiKey("wrong-key")
                .build()
        );

        // Verify - this returns a response with valid=false, not an exception
        assertFalse(response.getValid());
        assertEquals("Invalid API key", response.getMessage());
        assertFalse(response.hasConnector());
    }

    @Test
    void testMockValidateApiKey_NotFound() {
        // Setup mock for connector not found
        connectorServiceMock.mockValidateApiKeyNotFound("missing-conn", "any-key");

        // Call - should throw NOT_FOUND
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            connectorService.validateApiKey(
                ValidateApiKeyRequest.newBuilder()
                    .setConnectorId("missing-conn")
                    .setApiKey("any-key")
                    .build()
            );
        });

        assertEquals(io.grpc.Status.Code.NOT_FOUND, exception.getStatus().getCode());
        String description = exception.getStatus().getDescription();
        assertNotNull(description);
        assertTrue(description.contains("Connector not found"));
    }

    @Test
    void testMockValidateApiKey_PermissionDenied() {
        // Setup mock for permission denied (inactive connector/account)
        connectorServiceMock.mockValidateApiKeyPermissionDenied(
            "conn-123", 
            "api-key-456", 
            "Connector is inactive"
        );

        // Call - should throw PERMISSION_DENIED
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            connectorService.validateApiKey(
                ValidateApiKeyRequest.newBuilder()
                    .setConnectorId("conn-123")
                    .setApiKey("api-key-456")
                    .build()
            );
        });

        assertEquals(io.grpc.Status.Code.PERMISSION_DENIED, exception.getStatus().getCode());
        String description = exception.getStatus().getDescription();
        assertNotNull(description);
        assertTrue(description.contains("Connector is inactive"));
    }

    @Test
    void testMockValidateApiKey_RateLimited() {
        // Setup mock for rate limit exceeded
        connectorServiceMock.mockValidateApiKeyRateLimited("conn-123", "api-key-456");

        // Call - should throw RESOURCE_EXHAUSTED
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            connectorService.validateApiKey(
                ValidateApiKeyRequest.newBuilder()
                    .setConnectorId("conn-123")
                    .setApiKey("api-key-456")
                    .build()
            );
        });

        assertEquals(io.grpc.Status.Code.RESOURCE_EXHAUSTED, exception.getStatus().getCode());
        String description = exception.getStatus().getDescription();
        assertNotNull(description);
        assertTrue(description.contains("Rate limit exceeded"));
    }

    @Test
    void testMockValidateApiKey_Unauthenticated() {
        // Setup mock for unauthenticated (invalid API key)
        connectorServiceMock.mockValidateApiKeyUnauthenticated("conn-123", "invalid-key");

        // Call - should throw UNAUTHENTICATED
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            connectorService.validateApiKey(
                ValidateApiKeyRequest.newBuilder()
                    .setConnectorId("conn-123")
                    .setApiKey("invalid-key")
                    .build()
            );
        });

        assertEquals(io.grpc.Status.Code.UNAUTHENTICATED, exception.getStatus().getCode());
        String description = exception.getStatus().getDescription();
        assertNotNull(description);
        assertTrue(description.contains("Invalid API key"));
    }

    @Test
    void testMockValidateApiKey_RequestMatching() {
        // Setup mock for specific connector/key combination
        connectorServiceMock.mockValidateApiKey("conn-123", "api-key-456", "account-789");

        // Call with matching request - should succeed
        var response1 = connectorService.validateApiKey(
            ValidateApiKeyRequest.newBuilder()
                .setConnectorId("conn-123")
                .setApiKey("api-key-456")
                .build()
        );
        assertTrue(response1.getValid());

        // Call with different connector - should fail (no matching stub)
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            connectorService.validateApiKey(
                ValidateApiKeyRequest.newBuilder()
                    .setConnectorId("different-conn")
                    .setApiKey("api-key-456")
                    .build()
            );
        });

        // WireMock returns UNIMPLEMENTED when no matching stub is found
        assertEquals(io.grpc.Status.Code.UNIMPLEMENTED, exception.getStatus().getCode());
    }
}

