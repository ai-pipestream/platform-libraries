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

        // Verify
        assertEquals("test-account", account.getAccountId());
        assertEquals("Test Account", account.getName());
        assertEquals("Test description", account.getDescription());
        assertTrue(account.getActive());
        assertNotNull(account.getCreatedAt());
        assertNotNull(account.getUpdatedAt());
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

        // Verify
        assertEquals("inactive", account.getAccountId());
        assertFalse(account.getActive());
    }

    @Test
    void testMockGetAccount_NotFound() {
        // Setup mock for not found
        accountManagerMock.mockAccountNotFound("missing");

        // Call - should throw NOT_FOUND
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            accountService.getAccount(
                GetAccountRequest.newBuilder()
                    .setAccountId("missing")
                    .build()
            );
        });

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

        // Verify
        assertTrue(response.getCreated());
        assertEquals("new-account", response.getAccount().getAccountId());
        assertEquals("New Account", response.getAccount().getName());
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

        // Verify
        assertFalse(response.getCreated());
        assertEquals("existing", response.getAccount().getAccountId());
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

        // Verify
        assertTrue(response.getSuccess());
        assertEquals("Account inactivated successfully", response.getMessage());
        assertEquals(0, response.getDrivesAffected());
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

        // Verify
        assertFalse(response.getSuccess());
        assertTrue(response.getMessage().contains("not found"));
    }
}
