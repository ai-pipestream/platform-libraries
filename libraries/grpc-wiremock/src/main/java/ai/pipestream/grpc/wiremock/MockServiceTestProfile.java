package ai.pipestream.grpc.wiremock;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Test profile that enables mock services for testing.
 * 
 * This profile can be used with @TestProfile(MockServiceTestProfile.class)
 * to automatically switch to mock implementations during tests.
 */
public class MockServiceTestProfile implements QuarkusTestProfile {

    /**
     * Default constructor.
     */
    public MockServiceTestProfile() {
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Returns configuration that disables service registration and enables mock mode.
     */
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Disable automatic service registration to avoid classloader conflicts in tests
            "service.registration.enabled", "false",

            // Disable real platform registration service
            "quarkus.grpc.clients.platform-registration.host", "localhost",
            "quarkus.grpc.clients.platform-registration.port", "0", // Will be overridden by mock
            "quarkus.grpc.server.port", "0",

            // Enable mock mode
            "pipeline.test.mock-services.enabled", "true",
            "pipeline.test.mock-services.platform-registration.enabled", "true"
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getConfigProfile() {
        return "test";
    }
}
