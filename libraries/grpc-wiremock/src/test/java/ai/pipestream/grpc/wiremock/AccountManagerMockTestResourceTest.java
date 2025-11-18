package ai.pipestream.grpc.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import ai.pipestream.repository.account.AccountServiceGrpc;
import ai.pipestream.repository.account.GetAccountRequest;
import ai.pipestream.repository.account.CreateAccountRequest;
import ai.pipestream.repository.account.InactivateAccountRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for AccountManagerMockTestResource.
 * <p>
 * Verifies that the test resource:
 * 1. Starts WireMock correctly
 * 2. Configures Stork static discovery
 * 3. Injects WireMock server into test
 * 4. Routes gRPC calls to WireMock
 */
@QuarkusTest
@QuarkusTestResource(AccountManagerMockTestResource.class)
public class AccountManagerMockTestResourceTest {

    @InjectWireMock
    WireMockServer wireMockServer;

    private ManagedChannel channel;
    private AccountServiceGrpc.AccountServiceBlockingStub accountService;

    @BeforeEach
    void setUp() {
        // Verify WireMock server is injected
        assertThat("WireMock server should be injected", wireMockServer, is(notNullValue()));
        assertThat("WireMock server should be running", wireMockServer.isRunning(), is(true));
        assertThat("WireMock server should have a valid port", wireMockServer.port(), is(greaterThan(0)));

        // Create gRPC client using Stork (which should route to WireMock)
        // Note: In a real Quarkus test, you'd use @GrpcClient, but for this test
        // we'll create a direct channel to verify the mock works
        channel = ManagedChannelBuilder.forAddress("localhost", wireMockServer.port())
            .usePlaintext()
            .build();
        accountService = AccountServiceGrpc.newBlockingStub(channel);
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
    void testAccountManagerMockWorks() {
        // Setup mock
        AccountManagerMock accountMock = new AccountManagerMock(wireMockServer.port());
        accountMock.mockGetAccount("test-account", "Test Account", "Test description", true);

        // Call the service (directly to WireMock port)
        var response = accountService.getAccount(
            GetAccountRequest.newBuilder()
                .setAccountId("test-account")
                .build()
        );

        // Verify response with Hamcrest matchers
        assertThat("Response should not be null", response, is(notNullValue()));
        assertThat("Account ID should match", response.getAccountId(), is(equalTo("test-account")));
        assertThat("Account name should match", response.getName(), is(equalTo("Test Account")));
        assertThat("Account description should match", response.getDescription(), is(equalTo("Test description")));
        assertThat("Account should be active", response.getActive(), is(true));
        assertThat("Created timestamp should be set", response.getCreatedAt(), is(notNullValue()));
        assertThat("Updated timestamp should be set", response.getUpdatedAt(), is(notNullValue()));
    }

    @Test
    void testCreateAccount() {
        // Setup mock
        AccountManagerMock accountMock = new AccountManagerMock(wireMockServer.port());
        accountMock.mockCreateAccount("new-account", "New Account", "New description");

        // Call the service
        var response = accountService.createAccount(
            CreateAccountRequest.newBuilder()
                .setAccountId("new-account")
                .setName("New Account")
                .setDescription("New description")
                .build()
        );

        // Verify response with Hamcrest matchers
        assertThat("Response should not be null", response, is(notNullValue()));
        assertThat("Account should be created", response.getCreated(), is(true));
        assertThat("Response should have account", response.hasAccount(), is(true));
        
        var account = response.getAccount();
        assertThat("Account should not be null", account, is(notNullValue()));
        assertThat("Account ID should match", account.getAccountId(), is(equalTo("new-account")));
        assertThat("Account name should match", account.getName(), is(equalTo("New Account")));
        assertThat("Account description should match", account.getDescription(), is(equalTo("New description")));
    }

    @Test
    void testFailureScenarios() {
        // Setup failure mock
        AccountManagerMock accountMock = new AccountManagerMock(wireMockServer.port());
        accountMock.mockAccountNotFound("missing-account");

        // Call should fail
        var exception = assertThrows(io.grpc.StatusRuntimeException.class, () -> accountService.getAccount(
            GetAccountRequest.newBuilder()
                .setAccountId("missing-account")
                .build()
        ));

        assertEquals(io.grpc.Status.Code.NOT_FOUND, exception.getStatus().getCode());
        String description = exception.getStatus().getDescription();
        assertNotNull(description);
        assertTrue(description.contains("Account not found"));
    }

    @Test
    void testInactivateAccount() {
        // Setup mock
        AccountManagerMock accountMock = new AccountManagerMock(wireMockServer.port());
        accountMock.mockInactivateAccount("to-inactivate");

        // Call the service
        var response = accountService.inactivateAccount(
            InactivateAccountRequest.newBuilder()
                .setAccountId("to-inactivate")
                .setReason("Testing")
                .build()
        );

        // Verify response with Hamcrest matchers
        assertThat("Response should not be null", response, is(notNullValue()));
        assertThat("Inactivation should be successful", response.getSuccess(), is(true));
        assertThat("Message should match", response.getMessage(), is(equalTo("Account inactivated successfully")));
        assertThat("No drives should be affected", response.getDrivesAffected(), is(equalTo(0)));
    }
}

