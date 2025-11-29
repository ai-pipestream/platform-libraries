package ai.pipestream.grpc.wiremock.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.protobuf.Timestamp;
import ai.pipestream.repository.account.Account;
import ai.pipestream.repository.account.CreateAccountResponse;
import ai.pipestream.repository.account.InactivateAccountResponse;
import ai.pipestream.repository.account.GetAccountRequest;

import static ai.pipestream.grpc.wiremock.client.WireMockGrpcClient.*;

/**
 * Ready-to-use mock utilities for the Account Manager Service.
 * Refactored to use the container-based WireMock approach via WireMockGrpcClient.
 */
public class AccountManagerMock {

    private static final String SERVICE_NAME = "ai.pipestream.repository.account.AccountService";

    /**
     * Creates an account manager mock.
     * Note: In this client-only version, we don't hold a service instance.
     * The stubs are registered globally to the WireMock server connected via WireMock.configureFor().
     * 
     * Ensure WireMock.configureFor(host, port) is called before using this.
     */
    public AccountManagerMock() {
    }

    /**
     * Mock successful account creation.
     */
    public AccountManagerMock mockCreateAccount(String accountId, String name, String description) {
        WireMock.stubFor(
            grpcStubFor(SERVICE_NAME, "CreateAccount")
                .willReturn(aGrpcResponseWith(
                    CreateAccountResponse.newBuilder()
                        .setAccount(Account.newBuilder()
                            .setAccountId(accountId)
                            .setName(name)
                            .setDescription(description)
                            .setActive(true)
                            .setCreatedAt(Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1000).build())
                            .setUpdatedAt(Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1000).build())
                            .build())
                        .setCreated(true)
                        .build()
                ))
        );
        return this;
    }

    /**
     * Mock account already exists (not created).
     */
    public AccountManagerMock mockCreateAccountExists(String accountId, String name, String description) {
        WireMock.stubFor(
            grpcStubFor(SERVICE_NAME, "CreateAccount")
                .willReturn(aGrpcResponseWith(
                    CreateAccountResponse.newBuilder()
                        .setAccount(Account.newBuilder()
                            .setAccountId(accountId)
                            .setName(name)
                            .setDescription(description)
                            .setActive(true)
                            .build())
                        .setCreated(false)
                        .build()
                ))
        );
        return this;
    }

    /**
     * Mock account not found error.
     */
    public AccountManagerMock mockAccountNotFound(String accountId) {
        WireMock.stubFor(
            grpcStubFor(SERVICE_NAME, "GetAccount")
                .withRequestBody(equalToGrpcMessage(
                    GetAccountRequest.newBuilder().setAccountId(accountId).build()
                ))
                .willReturn(aGrpcErrorResponse(
                    io.grpc.Status.Code.NOT_FOUND.value(),
                    "Account not found: " + accountId
                ))
        );
        return this;
    }

    /**
     * Mock successful account retrieval.
     */
    public AccountManagerMock mockGetAccount(String accountId, String name, String description, boolean active) {
        WireMock.stubFor(
            grpcStubFor(SERVICE_NAME, "GetAccount")
                .withRequestBody(equalToGrpcMessage(
                    GetAccountRequest.newBuilder().setAccountId(accountId).build()
                ))
                .willReturn(aGrpcResponseWith(
                    Account.newBuilder()
                        .setAccountId(accountId)
                        .setName(name)
                        .setDescription(description)
                        .setActive(active)
                        .build()
                ))
        );
        return this;
    }

    /**
     * Mock successful account inactivation.
     */
    public AccountManagerMock mockInactivateAccount(String accountId) {
        WireMock.stubFor(
            grpcStubFor(SERVICE_NAME, "InactivateAccount")
                .willReturn(aGrpcResponseWith(
                    InactivateAccountResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("Account inactivated successfully")
                        .setDrivesAffected(0)
                        .build()
                ))
        );
        return this;
    }

    /**
     * Mock account inactivation failure (account not found).
     */
    public AccountManagerMock mockInactivateAccountNotFound(String accountId) {
        WireMock.stubFor(
            grpcStubFor(SERVICE_NAME, "InactivateAccount")
                .willReturn(aGrpcResponseWith(
                    InactivateAccountResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Account not found: " + accountId)
                        .setDrivesAffected(0)
                        .build()
                ))
        );
        return this;
    }
}