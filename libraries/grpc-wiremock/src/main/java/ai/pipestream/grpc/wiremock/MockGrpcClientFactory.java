package ai.pipestream.grpc.wiremock;

import ai.pipestream.common.grpc.GrpcClientFactory;
import ai.pipestream.platform.registration.MutinyPlatformRegistrationGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/**
 * Mock implementation of GrpcClientFactory that routes all gRPC calls to WireMock.
 * Use this in test profiles to bypass Consul service discovery.
 */
@Alternative
@ApplicationScoped
public class MockGrpcClientFactory implements GrpcClientFactory {

    private final int wireMockPort;
    private ManagedChannel mockChannel;

    /**
     * Creates a mock gRPC client factory.
     * 
     * <p>The WireMock port is read from the system property "test.wiremock.port".
     * If not set or set to 0, the factory will return failures for client requests.
     */
    public MockGrpcClientFactory() {
        // Get WireMock port from system property set by WireMockGrpcTestBase
        this.wireMockPort = Integer.parseInt(
            System.getProperty("test.wiremock.port", "0")
        );
        
        if (wireMockPort > 0) {
            this.mockChannel = ManagedChannelBuilder
                .forAddress("localhost", wireMockPort)
                .usePlaintext()
                .build();
        }
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Currently only supports "platform-registration-service".
     * Other services will return an UnsupportedOperationException.
     *
     * @param serviceName The name of the service
     * @param <T> The client type
     * @return a Uni containing the client stub
     * @throws IllegalStateException if WireMock port is not configured
     * @throws UnsupportedOperationException if the service is not supported
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Uni<T> getMutinyClientForService(String serviceName) {
        if (mockChannel == null) {
            return Uni.createFrom().failure(
                new IllegalStateException("WireMock port not configured for testing")
            );
        }

        // For now, we only support platform registration
        if ("platform-registration-service".equals(serviceName)) {
            return (Uni<T>) getPlatformRegistrationClient(serviceName);
        }
        
        return Uni.createFrom().failure(
            new UnsupportedOperationException("Mock not implemented for service: " + serviceName)
        );
    }

    /**
     * {@inheritDoc}
     *
     * @param serviceName The name of the platform registration service
     * @return a Uni containing the platform registration client stub
     * @throws IllegalStateException if WireMock port is not configured
     */
    @Override
    public Uni<MutinyPlatformRegistrationGrpc.MutinyPlatformRegistrationStub> getPlatformRegistrationClient(String serviceName) {
        if (mockChannel == null) {
            return Uni.createFrom().failure(
                new IllegalStateException("WireMock port not configured for testing")
            );
        }

        MutinyPlatformRegistrationGrpc.MutinyPlatformRegistrationStub client =
            MutinyPlatformRegistrationGrpc.newMutinyStub(mockChannel);

        return Uni.createFrom().item(client);
    }

    /**
     * Get a mock account service client.
     *
     * @param serviceName The name of the account service
     * @return a Uni containing the account service client stub
     * @throws IllegalStateException if WireMock port is not configured
     */
    public Uni<ai.pipestream.repository.account.MutinyAccountServiceGrpc.MutinyAccountServiceStub> getAccountServiceClient(String serviceName) {
        if (mockChannel == null) {
            return Uni.createFrom().failure(
                new IllegalStateException("WireMock port not configured for testing")
            );
        }

        ai.pipestream.repository.account.MutinyAccountServiceGrpc.MutinyAccountServiceStub client =
            ai.pipestream.repository.account.MutinyAccountServiceGrpc.newMutinyStub(mockChannel);

        return Uni.createFrom().item(client);
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Returns 1 if the mock channel is configured, 0 otherwise.
     */
    @Override
    public int getActiveServiceCount() {
        return mockChannel != null ? 1 : 0;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>No-op for mock implementation.
     *
     * @param serviceName The service name (ignored)
     */
    @Override
    public void evictChannel(String serviceName) {
        // No-op for mock
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Returns a description of the mock factory including the WireMock port.
     */
    @Override
    public String getCacheStats() {
        return "Mock gRPC client factory - routing to WireMock on port " + wireMockPort;
    }
}