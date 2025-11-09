package ai.pipestream.grpc.wiremock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import ai.pipestream.data.module.ServiceRegistrationMetadata;
import ai.pipestream.platform.registration.EventType;
import ai.pipestream.platform.registration.RegistrationEvent;
import ai.pipestream.registration.clients.PlatformRegistrationClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.common.QuarkusTestResource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

/**
 * Demonstrates how the dynamic gRPC registration client can register
 * against the {@link MockServiceFactory} server using only configuration.
 *
 * DISABLED: This test triggers a Quarkus classloader conflict where io.grpc.Channel
 * is loaded by different classloaders (QuarkusClassLoader vs app classloader).
 * This is a known Quarkus issue with @IfBuildProperty and dependency isolation in tests.
 * The test works conceptually but needs classloader configuration fixes.
 * See: https://github.com/quarkusio/quarkus/issues/... (file issue)
 */
@Disabled("Quarkus classloader conflict - io.grpc.Channel loaded by different classloaders")
@QuarkusTest
@TestProfile(PlatformRegistrationMockTestProfile.class)
@QuarkusTestResource(PlatformRegistrationClientMockTestResource.class)
public class PlatformRegistrationClientMockIntegrationTest {

    @Inject
    PlatformRegistrationClient registrationClient;

    @Test
    void serviceRegistrationStreamsAllEvents() {
        List<RegistrationEvent> events = registrationClient.registerService()
            .collect().asList()
            .await().atMost(Duration.ofSeconds(5));

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

        List<RegistrationEvent> events = registrationClient.registerModule(metadata)
            .collect().asList()
            .await().atMost(Duration.ofSeconds(5));

        assertEquals(10, events.size(), "Module registration should emit ten events");
        assertEquals(EventType.STARTED, events.get(0).getEventType());
        assertEquals(EventType.COMPLETED, events.get(events.size() - 1).getEventType());
        assertTrue(events.stream().noneMatch(e -> e.getEventType() == EventType.FAILED),
            "Module registration should not emit FAILED events");
    }
}
