package ai.pipestream.grpc.wiremock;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;
import java.util.Set;

/**
 * Test profile that enables gRPC mocking by replacing the real DynamicGrpcClientFactory
 * with MockGrpcClientFactory that routes all calls to WireMock.
 */
public class MockGrpcProfile implements QuarkusTestProfile {

    /**
     * Default constructor.
     */
    public MockGrpcProfile() {
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Returns configuration that routes all gRPC services to WireMock using static
     * service discovery and disables Consul.
     */
    @Override
    public Map<String, String> getConfigOverrides() {
        // Get WireMock port from system property
        String wireMockPort = System.getProperty("test.wiremock.port", "8080");

        return Map.ofEntries(
            // Use random port for tests
            Map.entry("quarkus.http.test-port", "0"),
            Map.entry("quarkus.grpc.server.use-separate-server", "false"),

            // Disable Consul service discovery - point services at WireMock
            Map.entry("quarkus.stork.platform-registration-service.service-discovery.type", "static"),
            Map.entry("quarkus.stork.platform-registration-service.service-discovery.address-list", "localhost:" + wireMockPort),

            Map.entry("quarkus.stork.account-manager.service-discovery.type", "static"),
            Map.entry("quarkus.stork.account-manager.service-discovery.address-list", "localhost:" + wireMockPort),

            // Disable automatic service registration during tests
            Map.entry("pipeline.registration.auto-register", "false")
        );
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Enables MockGrpcClientFactory as an alternative to the real GrpcClientFactory.
     */
    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(MockGrpcClientFactory.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getConfigProfile() {
        return "mock-grpc-test";
    }
}