package ai.pipestream.grpc.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
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
 * Tests for AccountManagerMock to verify the mock stubs work correctly.
 * <p>
 * This validates the mock framework before using it in other services.
 */
public class AccountManagerMockTest {

    private WireMockServer wireMockServer;
    private ManagedChannel channel;
    private AccountServiceGrpc.AccountServiceBlockingStub accountService;
    private AccountManagerMock accountManagerMock;

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

        // Create AccountManagerMock
        accountManagerMock = new AccountManagerMock(wireMockServer.port());

        // Create gRPC client
        channel = ManagedChannelBuilder.forAddress("localhost", wireMockServer.port())
            .usePlaintext()
            .build();
        accountService = AccountServiceGrpc.newBlockingStub(channel);
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
    void testMockGetAccount_Success() {
        // Setup mock
        accountManagerMock.mockGetAccount("test-account", "Test Account", "Test description", true);

        // Call
        var account = accountService.getAccount(
            GetAccountRequest.newBuilder()
                .setAccountId("test-account")
                .build()
        );

        // Verify with Hamcrest matchers
        assertThat("Account should not be null", account, is(notNullValue()));
        assertThat("Account ID should match", account.getAccountId(), is(equalTo("test-account")));
        assertThat("Account name should match", account.getName(), is(equalTo("Test Account")));
        assertThat("Account description should match", account.getDescription(), is(equalTo("Test description")));
        assertThat("Account should be active", account.getActive(), is(true));
        assertThat("Created timestamp should be set", account.getCreatedAt(), is(notNullValue()));
        assertThat("Updated timestamp should be set", account.getUpdatedAt(), is(notNullValue()));
        assertThat("Created timestamp should have seconds", account.getCreatedAt().getSeconds(), is(greaterThan(0L)));
        assertThat("Updated timestamp should have seconds", account.getUpdatedAt().getSeconds(), is(greaterThan(0L)));
    }

    @Test
    void testMockGetAccount_Inactive() {
        // Setup mock for inactive account
        accountManagerMock.mockGetAccount("inactive", "Inactive Account", "Inactive", false);

        // Call
        var account = accountService.getAccount(
            GetAccountRequest.newBuilder()
                .setAccountId("inactive")
                .build()
        );

        // Verify with Hamcrest matchers
        assertThat("Account should not be null", account, is(notNullValue()));
        assertThat("Account ID should match", account.getAccountId(), is(equalTo("inactive")));
        assertThat("Account should not be active", account.getActive(), is(false));
        assertThat("Account name should match", account.getName(), is(equalTo("Inactive Account")));
    }

    @Test
    void testMockGetAccount_NotFound() {
        // Setup mock for not found
        accountManagerMock.mockAccountNotFound("missing");

        // Call - should throw NOT_FOUND
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> accountService.getAccount(
            GetAccountRequest.newBuilder()
                .setAccountId("missing")
                .build()
        ));

        assertEquals(io.grpc.Status.Code.NOT_FOUND, exception.getStatus().getCode());
    }

    @Test
    void testMockCreateAccount_Success() {
        // Setup mock
        accountManagerMock.mockCreateAccount("new-account", "New Account", "New description");

        // Call
        var response = accountService.createAccount(
            CreateAccountRequest.newBuilder()
                .setAccountId("new-account")
                .setName("New Account")
                .setDescription("New description")
                .build()
        );

        // Verify with Hamcrest matchers
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
    void testMockCreateAccount_AlreadyExists() {
        // Setup mock for existing account
        accountManagerMock.mockCreateAccountExists("existing", "Existing", "Already exists");

        // Call
        var response = accountService.createAccount(
            CreateAccountRequest.newBuilder()
                .setAccountId("existing")
                .setName("Existing")
                .setDescription("Already exists")
                .build()
        );

        // Verify with Hamcrest matchers
        assertThat("Response should not be null", response, is(notNullValue()));
        assertThat("Account should not be created (already exists)", response.getCreated(), is(false));
        assertThat("Response should have account", response.hasAccount(), is(true));
        
        var account = response.getAccount();
        assertThat("Account should not be null", account, is(notNullValue()));
        assertThat("Account ID should match", account.getAccountId(), is(equalTo("existing")));
    }

    @Test
    void testMockInactivateAccount_Success() {
        // Setup mock
        accountManagerMock.mockInactivateAccount("to-inactivate");

        // Call
        var response = accountService.inactivateAccount(
            InactivateAccountRequest.newBuilder()
                .setAccountId("to-inactivate")
                .setReason("Testing")
                .build()
        );

        // Verify with Hamcrest matchers
        assertThat("Response should not be null", response, is(notNullValue()));
        assertThat("Inactivation should be successful", response.getSuccess(), is(true));
        assertThat("Message should match", response.getMessage(), is(equalTo("Account inactivated successfully")));
        assertThat("No drives should be affected", response.getDrivesAffected(), is(equalTo(0)));
    }

    @Test
    void testMockInactivateAccount_NotFound() {
        // Setup mock
        accountManagerMock.mockInactivateAccountNotFound("missing");

        // Call
        var response = accountService.inactivateAccount(
            InactivateAccountRequest.newBuilder()
                .setAccountId("missing")
                .setReason("Testing")
                .build()
        );

        // Verify with Hamcrest matchers
        assertThat("Response should not be null", response, is(notNullValue()));
        assertThat("Inactivation should not be successful", response.getSuccess(), is(false));
        assertThat("Message should contain 'not found'", response.getMessage(), containsString("not found"));
        assertThat("Message should not be empty", response.getMessage(), is(not(emptyString())));
    }
}
