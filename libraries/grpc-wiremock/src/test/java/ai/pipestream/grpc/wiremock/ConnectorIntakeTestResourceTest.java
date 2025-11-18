package ai.pipestream.grpc.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import ai.pipestream.repository.filesystem.upload.*;
import ai.pipestream.connector.intake.ConnectorAdminServiceGrpc;
import ai.pipestream.connector.intake.ValidateApiKeyRequest;
import ai.pipestream.repository.account.AccountServiceGrpc;
import ai.pipestream.repository.account.GetAccountRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for ConnectorIntakeTestResource.
 * <p>
 * Verifies that the composite test resource:
 * 1. Starts WireMock correctly
 * 2. Configures Stork static discovery for all three services (repository, connector, account-manager)
 * 3. Injects WireMock server into test
 * 4. Routes gRPC calls for all services to WireMock
 */
@QuarkusTest
@QuarkusTestResource(ConnectorIntakeTestResource.class)
public class ConnectorIntakeTestResourceTest {

    @InjectWireMock
    WireMockServer wireMockServer;

    private ManagedChannel channel;

    @BeforeEach
    void setUp() {
        // Verify WireMock server is injected
        assertNotNull(wireMockServer, "WireMock server should be injected");
        assertTrue(wireMockServer.isRunning(), "WireMock server should be running");
        assertTrue(wireMockServer.port() > 0, "WireMock server should have a valid port");

        // Create gRPC client (directly to WireMock port for this test)
        channel = ManagedChannelBuilder.forAddress("localhost", wireMockServer.port())
            .usePlaintext()
            .build();
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
    void testRepositoryServiceMockWorks() {
        // Setup repository service mock
        RepositoryServiceMock repositoryMock = new RepositoryServiceMock(wireMockServer.port());
        repositoryMock.mockInitiateUpload("test-node-123", "upload-456");

        // Call repository service
        var uploadService = NodeUploadServiceGrpc.newBlockingStub(channel);
        var response = uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("test-file.txt")
                .build()
        );

        // Verify response
        assertEquals("test-node-123", response.getNodeId());
        assertEquals("upload-456", response.getUploadId());
    }

    @Test
    void testConnectorServiceMockWorks() {
        // Setup connector service mock
        ConnectorServiceMock connectorMock = new ConnectorServiceMock(wireMockServer.port());
        connectorMock.mockValidateApiKey("test-connector", "test-api-key", "test-account");

        // Call connector service
        var connectorService = ConnectorAdminServiceGrpc.newBlockingStub(channel);
        var response = connectorService.validateApiKey(
            ValidateApiKeyRequest.newBuilder()
                .setConnectorId("test-connector")
                .setApiKey("test-api-key")
                .build()
        );

        // Verify response
        assertTrue(response.getValid());
        assertEquals("test-connector", response.getConnector().getConnectorId());
    }

    @Test
    void testAccountManagerMockWorks() {
        // Setup account manager mock
        AccountManagerMock accountMock = new AccountManagerMock(wireMockServer.port());
        accountMock.mockGetAccount("test-account", "Test Account", "Test description", true);

        // Call account manager service
        var accountService = AccountServiceGrpc.newBlockingStub(channel);
        var response = accountService.getAccount(
            GetAccountRequest.newBuilder()
                .setAccountId("test-account")
                .build()
        );

        // Verify response
        assertEquals("test-account", response.getAccountId());
        assertEquals("Test Account", response.getName());
        assertTrue(response.getActive());
    }

    @Test
    void testAllServicesOnSameWireMockServer() {
        // Verify all services use the same WireMock server port
        int port = wireMockServer.port();

        // Setup mocks for all three services
        RepositoryServiceMock repositoryMock = new RepositoryServiceMock(port);
        ConnectorServiceMock connectorMock = new ConnectorServiceMock(port);
        AccountManagerMock accountMock = new AccountManagerMock(port);

        repositoryMock.mockInitiateUpload("node-1", "upload-1");
        connectorMock.mockValidateApiKey("conn-1", "key-1", "acc-1");
        accountMock.mockGetAccount("acc-1", "Account 1", "Desc", true);

        // All should work on the same port - verify by making actual calls
        assertTrue(port > 0);
        
        // Verify repository service works
        var uploadService = NodeUploadServiceGrpc.newBlockingStub(channel);
        var uploadResponse = uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("test.txt")
                .build()
        );
        assertEquals("node-1", uploadResponse.getNodeId());
        
        // Verify connector service works
        var connectorService = ConnectorAdminServiceGrpc.newBlockingStub(channel);
        var connectorResponse = connectorService.validateApiKey(
            ValidateApiKeyRequest.newBuilder()
                .setConnectorId("conn-1")
                .setApiKey("key-1")
                .build()
        );
        assertTrue(connectorResponse.getValid());
        
        // Verify account service works
        var accountService = AccountServiceGrpc.newBlockingStub(channel);
        var accountResponse = accountService.getAccount(
            GetAccountRequest.newBuilder()
                .setAccountId("acc-1")
                .build()
        );
        assertEquals("acc-1", accountResponse.getAccountId());
    }
}

