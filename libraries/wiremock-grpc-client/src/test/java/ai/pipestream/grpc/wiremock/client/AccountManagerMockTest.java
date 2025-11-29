package ai.pipestream.grpc.wiremock.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import ai.pipestream.repository.account.AccountServiceGrpc;
import ai.pipestream.repository.account.GetAccountRequest;
import ai.pipestream.repository.account.CreateAccountRequest;
import ai.pipestream.repository.account.InactivateAccountRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AccountManagerMock to verify the mock stubs work correctly.
 * This validates the mock framework before using it in other services.
 * <p>
 * This version uses an external WireMock server managed by Testcontainers.
 */
@QuarkusTest
@QuarkusTestResource(WireMockServerTestResource.class)
public class AccountManagerMockTest {

    @ConfigProperty(name = "wiremock.url")
    String wiremockUrl;

    // Remove @ConfigProperty(name = "wiremock.grpc.port") int wiremockGrpcPort;

    private ManagedChannel channel;
    private AccountServiceGrpc.AccountServiceBlockingStub accountService;
    private AccountManagerMock accountManagerMock;

    @BeforeEach
    void setUp() {
        System.out.println("WireMock URL: " + wiremockUrl);
        // System.out.println("WireMock gRPC Port: " + wiremockGrpcPort); // Removed

        // Extract HTTP port from wiremock.url (http://localhost:port)
        int httpPort = Integer.parseInt(wiremockUrl.substring(wiremockUrl.lastIndexOf(":") + 1));
        
        // Verify HTTP connectivity
        try {
            java.net.URL url = new java.net.URL(wiremockUrl + "/__admin/health");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();
            System.out.println("WireMock Health Check Response: " + responseCode);
            if (responseCode != 200) {
                throw new RuntimeException("WireMock is not healthy: " + responseCode);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to WireMock HTTP", e);
        }

        // Configure WireMock client to point to the container
        WireMock.configureFor("localhost", httpPort);
        WireMock.reset();

        // Initialize the mock helper
        accountManagerMock = new AccountManagerMock();

        // Create gRPC client
        // Connect to the dynamically assigned HTTP port for gRPC (multiplexed)
        channel = ManagedChannelBuilder.forAddress("localhost", httpPort)
            .usePlaintext()
            .build();
        accountService = AccountServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Test
    void testMockGetAccount_Success() {
        // Setup mock using helper
        accountManagerMock.mockGetAccount("test-account", "Test Account", "Test description", true);

        GetAccountRequest request = GetAccountRequest.newBuilder()
                .setAccountId("test-account")
                .build();

        // Call
        var account = accountService.getAccount(request);

        // Verify
        assertThat("Account should not be null", account, is(notNullValue()));
        assertThat("Account ID should match", account.getAccountId(), is(equalTo("test-account")));
        assertThat("Account name should match", account.getName(), is(equalTo("Test Account")));
        assertThat("Account description should match", account.getDescription(), is(equalTo("Test description")));
        assertThat("Account should be active", account.getActive(), is(true));
    }

    @Test
    void testMockGetAccount_Inactive() {
        // Setup mock using helper
        accountManagerMock.mockGetAccount("inactive", "Inactive Account", "Inactive", false);

        GetAccountRequest request = GetAccountRequest.newBuilder()
                .setAccountId("inactive")
                .build();

        // Call
        var account = accountService.getAccount(request);

        // Verify
        assertThat("Account should not be null", account, is(notNullValue()));
        assertThat("Account ID should match", account.getAccountId(), is(equalTo("inactive")));
        assertThat("Account should not be active", account.getActive(), is(false));
        assertThat("Account name should match", account.getName(), is(equalTo("Inactive Account")));
    }

    @Test
    void testMockGetAccount_NotFound() {
        // Setup mock using helper
        accountManagerMock.mockAccountNotFound("missing");

        GetAccountRequest request = GetAccountRequest.newBuilder()
                .setAccountId("missing")
                .build();

        // Call - should throw NOT_FOUND
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> accountService.getAccount(request));

        assertEquals(io.grpc.Status.Code.NOT_FOUND, exception.getStatus().getCode());
    }

    @Test
    void testMockCreateAccount_Success() {
        // Setup mock using helper
        accountManagerMock.mockCreateAccount("new-account", "New Account", "New description");

        CreateAccountRequest request = CreateAccountRequest.newBuilder()
                .setAccountId("new-account")
                .setName("New Account")
                .setDescription("New description")
                .build();

        // Call
        var response = accountService.createAccount(request);

        // Verify
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
        // Setup mock using helper
        accountManagerMock.mockCreateAccountExists("existing", "Existing", "Already exists");

        CreateAccountRequest request = CreateAccountRequest.newBuilder()
                .setAccountId("existing")
                .setName("Existing")
                .setDescription("Already exists")
                .build();

        // Call
        var response = accountService.createAccount(request);

        // Verify
        assertThat("Response should not be null", response, is(notNullValue()));
        assertThat("Account should not be created (already exists)", response.getCreated(), is(false));
        assertThat("Response should have account", response.hasAccount(), is(true));
        
        var account = response.getAccount();
        assertThat("Account should not be null", account, is(notNullValue()));
        assertThat("Account ID should match", account.getAccountId(), is(equalTo("existing")));
    }

    @Test
    void testMockInactivateAccount_Success() {
        // Setup mock using helper
        accountManagerMock.mockInactivateAccount("to-inactivate");

        InactivateAccountRequest request = InactivateAccountRequest.newBuilder()
                .setAccountId("to-inactivate")
                .setReason("Testing")
                .build();

        // Call
        var response = accountService.inactivateAccount(request);

        // Verify
        assertThat("Response should not be null", response, is(notNullValue()));
        assertThat("Inactivation should be successful", response.getSuccess(), is(true));
        assertThat("Message should match", response.getMessage(), is(equalTo("Account inactivated successfully")));
    }

    @Test
    void testMockInactivateAccount_NotFound() {
        // Setup mock using helper
        accountManagerMock.mockInactivateAccountNotFound("missing");

        InactivateAccountRequest request = InactivateAccountRequest.newBuilder()
                .setAccountId("missing")
                .setReason("Testing")
                .build();

        // Call
        var response = accountService.inactivateAccount(request);

        // Verify
        assertThat("Response should not be null", response, is(notNullValue()));
        assertThat("Inactivation should not be successful", response.getSuccess(), is(false));
        assertThat("Message should contain 'not found'", response.getMessage(), containsString("not found"));
        assertThat("Message should not be empty", response.getMessage(), is(not(emptyString())));
    }
}