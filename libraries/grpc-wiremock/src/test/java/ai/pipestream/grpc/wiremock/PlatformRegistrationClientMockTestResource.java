package ai.pipestream.grpc.wiremock;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

/**
 * Starts the {@link MockServiceFactory} gRPC server and points the
 * dynamic-grpc registration client at it for tests.
 */
public class PlatformRegistrationClientMockTestResource implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        MockServiceFactory.startMockPlatformRegistrationService();

        int mockPort = MockServiceFactory.getMockServer().getGrpcPort();

        return Map.ofEntries(
            Map.entry("service.registration.enabled", "true"),
            Map.entry("service.registration.service-name", "mock-test-service"),
            Map.entry("service.registration.description", "Mock service used for registration tests"),
            Map.entry("service.registration.service-type", "APPLICATION"),
            Map.entry("service.registration.host", "localhost"),
            Map.entry("service.registration.port", "39001"),
            Map.entry("service.registration.capabilities", "testing,mock"),
            Map.entry("service.registration.tags", "mock,test"),

            // Avoid port clashes with other tests
            Map.entry("quarkus.grpc.server.port", "0"),

            // Stork static discovery for the mock registration service
            Map.entry("quarkus.stork.platform-registration-service.service-discovery.type", "static"),
            Map.entry("quarkus.stork.platform-registration-service.service-discovery.address-list", "localhost:" + mockPort)
        );
    }

    @Override
    public void stop() {
        MockServiceFactory.stopMockPlatformRegistrationService();
    }
}
