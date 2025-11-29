package ai.pipestream.registration.clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import ai.pipestream.data.module.ServiceRegistrationMetadata;
import ai.pipestream.platform.registration.EventType;
import ai.pipestream.platform.registration.RegistrationEvent;
import ai.pipestream.grpc.wiremock.client.WireMockServerTestResource;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.smallrye.stork.Stork;
import io.smallrye.stork.api.ServiceDefinition;
import io.smallrye.stork.integration.DefaultStorkInfrastructure;
import io.smallrye.stork.spi.config.SimpleServiceConfig;

import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Demonstrates how the dynamic gRPC registration client can register
 * against the containerized WireMock server (specifically the streaming sidecar).
 */
@QuarkusTest
@TestProfile(PlatformRegistrationClientMockIntegrationTest.EnableRegistrationProfile.class)
@QuarkusTestResource(WireMockServerTestResource.class)
public class PlatformRegistrationClientMockIntegrationTest {

    public static class EnableRegistrationProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "service.registration.enabled", "true",
                "service.registration.service-name", "mock-service",
                "service.registration.port", "8080",
                "service.registration.description", "Test Description",
                "service.registration.capabilities", "foo,bar",
                "service.registration.tags", "test,mock"
            );
        }
    }

    @Inject
    PlatformRegistrationClient registrationClient;

    @ConfigProperty(name = "wiremock.url")
    String wiremockUrl;

    @ConfigProperty(name = "wiremock.streaming.port")
    int streamingPort;

    @BeforeEach
    void setup() {
        int httpPort = Integer.parseInt(wiremockUrl.substring(wiremockUrl.lastIndexOf(":") + 1));
        WireMock.configureFor("localhost", httpPort);
        WireMock.reset();

        // Initialize Stork
        if (Stork.getInstance() != null) {
            Stork.shutdown();
        }
        Stork.initialize(new DefaultStorkInfrastructure());

        // Point "platform-registration-service" to the streaming port of our WireMock container
        // This ensures the client talks to the DirectWireMockGrpcServer which supports streaming
        Map<String, String> params = Map.of("address-list", "localhost:" + streamingPort);
        var discoveryConfig = new SimpleServiceConfig.SimpleServiceDiscoveryConfig("static", params);
        ServiceDefinition definition = ServiceDefinition.of(discoveryConfig);
        Stork.getInstance().defineIfAbsent("platform-registration-service", definition);
    }

    @Test
    void serviceRegistrationStreamsAllEvents() {
        // The DirectWireMockGrpcServer (sidecar) has hardcoded logic to stream 6 events for registerService
        
        List<RegistrationEvent> events = registrationClient.registerService()
            .collect().asList()
            .await().atMost(Duration.ofSeconds(10));

        assertEquals(6, events.size(), "Service registration should emit six events");
        assertEquals(EventType.STARTED, events.get(0).getEventType());
        assertEquals(EventType.COMPLETED, events.get(events.size() - 1).getEventType());
        assertFalse(events.stream().anyMatch(e -> e.getEventType() == EventType.FAILED),
            "No FAILED events should be emitted");
    }

    @Test
    void moduleRegistrationStreamsAllEvents() {
        ServiceRegistrationMetadata metadata = ServiceRegistrationMetadata.newBuilder()
            .setModuleName("mock-module")
            .setDescription("Mock module used in registration test")
            .build();

        // The DirectWireMockGrpcServer (sidecar) has hardcoded logic to stream 10 events for registerModule
        
        List<RegistrationEvent> events = registrationClient.registerModule(metadata)
            .collect().asList()
            .await().atMost(Duration.ofSeconds(10));

        assertEquals(10, events.size(), "Module registration should emit ten events");
        assertEquals(EventType.STARTED, events.get(0).getEventType());
        assertEquals(EventType.COMPLETED, events.get(events.size() - 1).getEventType());
        assertTrue(events.stream().noneMatch(e -> e.getEventType() == EventType.FAILED),
            "Module registration should not emit FAILED events");
    }
}