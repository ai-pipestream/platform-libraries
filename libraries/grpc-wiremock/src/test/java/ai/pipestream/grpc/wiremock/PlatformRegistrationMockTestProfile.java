package ai.pipestream.grpc.wiremock;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Test profile for PlatformRegistrationClientMockIntegrationTest.
 * Uses application-platform-registration-mock.properties to enable service registration bean,
 * but overrides enabled=false at runtime to prevent onStart() from triggering classloader conflicts.
 */
public class PlatformRegistrationMockTestProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "platform-registration-mock";
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Disable auto-registration at startup to avoid classloader conflicts
            // The test will manually call registerService()
            "service.registration.enabled", "false"
        );
    }
}
