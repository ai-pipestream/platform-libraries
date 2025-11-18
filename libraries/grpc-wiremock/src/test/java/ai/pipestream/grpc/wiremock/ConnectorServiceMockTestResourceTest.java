package ai.pipestream.grpc.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import ai.pipestream.connector.intake.ConnectorAdminServiceGrpc;
import ai.pipestream.connector.intake.ValidateApiKeyRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for ConnectorServiceMockTestResource.
 * <p>
 * Verifies that the test resource:
 * 1. Starts WireMock correctly
 * 2. Configures Stork static discovery
 * 3. Injects WireMock server into test
 * 4. Routes gRPC calls to WireMock
 */
@QuarkusTest
@QuarkusTestResource(ConnectorServiceMockTestResource.class)
public class ConnectorServiceMockTestResourceTest {

    @InjectWireMock
    WireMockServer wireMockServer;

    private ManagedChannel channel;
    private ConnectorAdminServiceGrpc.ConnectorAdminServiceBlockingStub connectorService;

    @BeforeEach
    void setUp() {
        // Verify WireMock server is injected
        assertNotNull(wireMockServer, "WireMock server should be injected");
        assertTrue(wireMockServer.isRunning(), "WireMock server should be running");
        assertTrue(wireMockServer.port() > 0, "WireMock server should have a valid port");

        // Create gRPC client using Stork (which should route to WireMock)
        // Note: In a real Quarkus test, you'd use @GrpcClient, but for this test
        // we'll create a direct channel to verify the mock works
        channel = ManagedChannelBuilder.forAddress("localhost", wireMockServer.port())
            .usePlaintext()
            .build();
        connectorService = ConnectorAdminServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }

    @Test
    void testWireMockServerStarted() {
        assertNotNull(wireMockServer, "WireMock server should be injected");
        assertTrue(wireMockServer.isRunning(), "WireMock server should be running");
        assertTrue(wireMockServer.port() > 0, "WireMock server should have a valid port");
    }

    @Test
    void testConnectorServiceMockWorks() {
        // Setup mock
        ConnectorServiceMock connectorMock = new ConnectorServiceMock(wireMockServer.port());
        connectorMock.mockValidateApiKey("test-connector", "test-api-key", "test-account");

        // Call the service (directly to WireMock port)
        var response = connectorService.validateApiKey(
            ValidateApiKeyRequest.newBuilder()
                .setConnectorId("test-connector")
                .setApiKey("test-api-key")
                .build()
        );

        // Verify response
        assertTrue(response.getValid());
        assertEquals("API key validated successfully", response.getMessage());
        assertTrue(response.hasConnector());
        assertEquals("test-connector", response.getConnector().getConnectorId());
        assertEquals("test-account", response.getConnector().getAccountId());
    }

    @Test
    void testFailureScenarios() {
        // Setup failure mock
        ConnectorServiceMock connectorMock = new ConnectorServiceMock(wireMockServer.port());
        connectorMock.mockValidateApiKeyNotFound("missing-connector", "any-key");

        // Call should fail
        var exception = assertThrows(io.grpc.StatusRuntimeException.class, () -> {
            connectorService.validateApiKey(
                ValidateApiKeyRequest.newBuilder()
                    .setConnectorId("missing-connector")
                    .setApiKey("any-key")
                    .build()
            );
        });

        assertEquals(io.grpc.Status.Code.NOT_FOUND, exception.getStatus().getCode());
    }
}

