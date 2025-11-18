package ai.pipestream.grpc.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import ai.pipestream.repository.filesystem.upload.*;
import ai.pipestream.connector.intake.ConnectorAdminServiceGrpc;
import ai.pipestream.connector.intake.ValidateApiKeyRequest;
import ai.pipestream.repository.account.AccountServiceGrpc;
import ai.pipestream.repository.account.GetAccountRequest;
import ai.pipestream.repository.account.CreateAccountRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Tests for ServiceMocks to verify the factory and setupDefaults() method.
 */
public class ServiceMocksTest {

    private WireMockServer wireMockServer;
    private ManagedChannel channel;
    private ServiceMocks serviceMocks;

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

        // Create ServiceMocks factory
        serviceMocks = new ServiceMocks(wireMockServer);

        // Create gRPC client
        channel = ManagedChannelBuilder.forAddress("localhost", wireMockServer.port())
            .usePlaintext()
            .build();
    }

    @AfterEach
    void tearDown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void testSetupDefaults_ConfiguresAllServices() {
        // Setup defaults for all services
        ServiceMocks result = serviceMocks.setupDefaults();

        // Verify method chaining
        assertThat("Should return this for method chaining", result, is(sameInstance(serviceMocks)));

        // Verify repository service defaults are set
        var uploadService = NodeUploadServiceGrpc.newBlockingStub(channel);
        var uploadResponse = uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("test-file.txt")
                .build()
        );
        assertThat("Repository service should have defaults", uploadResponse, is(notNullValue()));
        assertThat("Node ID should not be empty", uploadResponse.getNodeId(), is(not(emptyString())));
        assertThat("Upload ID should not be empty", uploadResponse.getUploadId(), is(not(emptyString())));
        assertThat("State should be PENDING", uploadResponse.getState(), is(UploadState.UPLOAD_STATE_PENDING));

        // Verify connector service defaults are set
        var connectorService = ConnectorAdminServiceGrpc.newBlockingStub(channel);
        var connectorResponse = connectorService.validateApiKey(
            ValidateApiKeyRequest.newBuilder()
                .setConnectorId("default-connector")
                .setApiKey("default-api-key")
                .build()
        );
        assertThat("Connector service should have defaults", connectorResponse, is(notNullValue()));
        assertThat("Response should be valid", connectorResponse.getValid(), is(true));
        assertThat("Response should have connector", connectorResponse.hasConnector(), is(true));
        assertThat("Connector ID should match", connectorResponse.getConnector().getConnectorId(), is(equalTo("default-connector")));
        assertThat("Account ID should match", connectorResponse.getConnector().getAccountId(), is(equalTo("default-account")));

        // Verify account manager defaults are set
        var accountService = AccountServiceGrpc.newBlockingStub(channel);
        
        // Test GetAccount
        var getAccountResponse = accountService.getAccount(
            GetAccountRequest.newBuilder()
                .setAccountId("default-account")
                .build()
        );
        assertThat("Account service should have defaults", getAccountResponse, is(notNullValue()));
        assertThat("Account ID should match", getAccountResponse.getAccountId(), is(equalTo("default-account")));
        assertThat("Account name should match", getAccountResponse.getName(), is(equalTo("Default Account")));
        assertThat("Account should be active", getAccountResponse.getActive(), is(true));

        // Test CreateAccount
        var createAccountResponse = accountService.createAccount(
            CreateAccountRequest.newBuilder()
                .setAccountId("default-account")
                .setName("Default Account")
                .setDescription("Default test account")
                .build()
        );
        assertThat("Create account should work", createAccountResponse, is(notNullValue()));
        assertThat("Account should be created", createAccountResponse.getCreated(), is(true));
        assertThat("Created account ID should match", createAccountResponse.getAccount().getAccountId(), is(equalTo("default-account")));
    }

    @Test
    void testSetupDefaults_AllMocksAreAccessible() {
        // Setup defaults
        serviceMocks.setupDefaults();

        // Verify all mocks are accessible and not null
        assertThat("Platform registration mock should be accessible", 
            serviceMocks.platformRegistration(), is(notNullValue()));
        assertThat("Account manager mock should be accessible", 
            serviceMocks.accountManager(), is(notNullValue()));
        assertThat("Repository service mock should be accessible", 
            serviceMocks.repository(), is(notNullValue()));
        assertThat("Connector service mock should be accessible", 
            serviceMocks.connector(), is(notNullValue()));
        assertThat("Mapping service mock should be accessible", 
            serviceMocks.mapping(), is(notNullValue()));
        assertThat("Engine service mock should be accessible", 
            serviceMocks.engine(), is(notNullValue()));
        assertThat("Design mode service mock should be accessible", 
            serviceMocks.designMode(), is(notNullValue()));
    }

    @Test
    void testSetupDefaults_CanBeCalledMultipleTimes() {
        // Setup defaults multiple times
        serviceMocks.setupDefaults();
        serviceMocks.setupDefaults();
        serviceMocks.setupDefaults();

        // Should still work - verify repository service
        var uploadService = NodeUploadServiceGrpc.newBlockingStub(channel);
        var response = uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("test-file.txt")
                .build()
        );

        assertThat("Should work after multiple calls", response, is(notNullValue()));
        assertThat("Node ID should not be empty", response.getNodeId(), is(not(emptyString())));
    }
}

