package ai.pipestream.grpc.wiremock.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import ai.pipestream.connector.intake.ConnectorRegistration;
import ai.pipestream.connector.intake.ConnectorAdminServiceGrpc;
import ai.pipestream.connector.intake.ValidateApiKeyRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConnectorServiceMock to verify the mock stubs work correctly.
 * <p>
 * This validates the mock framework before using it in other services.
 */
@QuarkusTest
@QuarkusTestResource(WireMockServerTestResource.class)
public class ConnectorServiceMockTest {

    private static final Logger LOG = Logger.getLogger(ConnectorServiceMockTest.class);

    @ConfigProperty(name = "wiremock.url")
    String wiremockUrl;

    private ManagedChannel channel;
    private ConnectorAdminServiceGrpc.ConnectorAdminServiceBlockingStub connectorService;
    private ConnectorServiceMock connectorServiceMock;

    @BeforeEach
    void setUp() {
        LOG.info("WireMock URL: " + wiremockUrl);

        // Extract HTTP port from wiremock.url (http://localhost:port)
        int httpPort = Integer.parseInt(wiremockUrl.substring(wiremockUrl.lastIndexOf(":") + 1));
        
        // Configure WireMock client to point to the container
        WireMock.configureFor("localhost", httpPort);
        WireMock.reset();

        // Initialize the mock helper
        connectorServiceMock = new ConnectorServiceMock();

        // Create gRPC client using the HTTP port (multiplexed)
        channel = ManagedChannelBuilder.forAddress("localhost", httpPort)
            .usePlaintext()
            .build();
        connectorService = ConnectorAdminServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdown();
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

        // Verify with Hamcrest matchers
        assertThat("Response should be valid", response.getValid(), is(true));
        assertThat("Message should match", response.getMessage(), is(equalTo("API key validated successfully")));
        assertThat("Response should have connector", response.hasConnector(), is(true));
        
        ConnectorRegistration connector = response.getConnector();
        assertThat("Connector should not be null", connector, is(notNullValue()));
        assertThat("Connector ID should match", connector.getConnectorId(), is(equalTo("conn-123")));
        assertThat("Connector name should match", connector.getConnectorName(), is(equalTo("Test Connector")));
        assertThat("Connector type should match", connector.getConnectorType(), is(equalTo("filesystem")));
        assertThat("Account ID should match", connector.getAccountId(), is(equalTo("account-789")));
        assertThat("API key should match", connector.getApiKey(), is(equalTo("api-key-456")));
        assertThat("Connector should be active", connector.getActive(), is(true));
        assertThat("S3 bucket should not be empty", connector.getS3Bucket(), is(not(emptyString())));
        assertThat("S3 base path should not be empty", connector.getS3BasePath(), is(not(emptyString())));
        assertThat("Max file size should be positive", connector.getMaxFileSize(), is(greaterThan(0L)));
        assertThat("Rate limit should be positive", connector.getRateLimitPerMinute(), is(greaterThan(0L)));
        assertThat("Created timestamp should be set", connector.hasCreated(), is(true));
        assertThat("Updated timestamp should be set", connector.hasUpdated(), is(true));
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

        // Verify with Hamcrest matchers
        assertThat("Response should be valid", response.getValid(), is(true));
        assertThat("Response should have connector", response.hasConnector(), is(true));
        
        ConnectorRegistration connector = response.getConnector();
        assertThat("Connector should not be null", connector, is(notNullValue()));
        assertThat("Connector ID should match", connector.getConnectorId(), is(equalTo("custom-conn")));
        assertThat("Connector name should match", connector.getConnectorName(), is(equalTo("Custom Connector")));
        assertThat("Connector type should match", connector.getConnectorType(), is(equalTo("s3")));
        assertThat("Account ID should match", connector.getAccountId(), is(equalTo("custom-account")));
        assertThat("API key should match", connector.getApiKey(), is(equalTo("custom-key")));
        assertThat("Connector should be active", connector.getActive(), is(true));
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
        assertThat("Response should be invalid", response.getValid(), is(false));
        assertThat("Message should match", response.getMessage(), is(equalTo("Invalid API key")));
        assertThat("Response should not have connector", response.hasConnector(), is(false));
        assertThat("Response should not be null", response, is(notNullValue()));
    }

    @Test
    void testMockValidateApiKey_NotFound() {
        // Setup mock for connector not found
        connectorServiceMock.mockValidateApiKeyNotFound("missing-conn", "any-key");

        // Call - should throw NOT_FOUND
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> connectorService.validateApiKey(
            ValidateApiKeyRequest.newBuilder()
                .setConnectorId("missing-conn")
                .setApiKey("any-key")
                .build()
        ));

        assertEquals(io.grpc.Status.Code.NOT_FOUND, exception.getStatus().getCode());
        String description = exception.getStatus().getDescription();
        assertNotNull(description);
        // Verify our error mapping correctly propagates the message
        assertTrue(description.contains("Connector not found"), "Description was: " + description);
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
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> connectorService.validateApiKey(
            ValidateApiKeyRequest.newBuilder()
                .setConnectorId("conn-123")
                .setApiKey("api-key-456")
                .build()
        ));

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
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> connectorService.validateApiKey(
            ValidateApiKeyRequest.newBuilder()
                .setConnectorId("conn-123")
                .setApiKey("api-key-456")
                .build()
        ));

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
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> connectorService.validateApiKey(
            ValidateApiKeyRequest.newBuilder()
                .setConnectorId("conn-123")
                .setApiKey("invalid-key")
                .build()
        ));

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
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> connectorService.validateApiKey(
            ValidateApiKeyRequest.newBuilder()
                .setConnectorId("different-conn")
                .setApiKey("api-key-456")
                .build()
        ));

        // WireMock returns UNIMPLEMENTED when no matching stub is found
        assertEquals(io.grpc.Status.Code.UNIMPLEMENTED, exception.getStatus().getCode());
    }
}
