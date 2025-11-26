package ai.pipestream.kafka.connector.it;

import ai.pipestream.api.annotation.ProtobufChannel;
import ai.pipestream.api.annotation.ProtobufIncoming;
import ai.pipestream.platform.registration.ServiceRegistered;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.junit.jupiter.api.Test;
import org.awaitility.Awaitility;

import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class ProtobufKafkaConnectorTest {

    // MUTINY EMITTER EXAMPLE (Reactive/Async)
    // Both MutinyEmitter and regular Emitter are supported.
    // Choose based on your preference: MutinyEmitter for reactive/async, Emitter for imperative/sync.
    //
    // Example with regular Emitter:
    // @Inject @Channel("test-out") @ProtobufChannel("test-out") Emitter<MyMessage> emitter;
    // emitter.send(message);
    //
    // Example with MutinyEmitter:
    // @Inject @Channel("test-out") @ProtobufChannel("test-out") MutinyEmitter<MyMessage> emitter;
    // emitter.sendAndAwait(message);
    //
    @Inject
    @Channel("test-service-events-out")
    @ProtobufChannel(value = "test-service-events-out", properties = { "topic=test-service-events" })
    MutinyEmitter<ServiceRegistered> emitter;

    @Inject
    TestConsumer consumer;

    @Test
    public void testProtobufMessageRoundTrip() {
        // Create
        ServiceRegistered message = ServiceRegistered.newBuilder()
                .setServiceName("test-service")
                .setServiceId("test-id-123")
                .build();

        // Send using MutinyEmitter (reactive/async pattern)
        emitter.sendAndAwait(message);

        // Verify
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    assertThat(consumer.getReceivedMessages())
                            .isNotEmpty()
                            .last()
                            .satisfies(msg -> {
                                assertThat(msg.getServiceName()).isEqualTo("test-service");
                                assertThat(msg.getServiceId()).isEqualTo("test-id-123");
                            });
                });
    }

    @ApplicationScoped
    public static class TestConsumer {

        private final List<ServiceRegistered> messages = new CopyOnWriteArrayList<>();

        // Double annotation pattern:
        // @Incoming = Wiring (Standard SmallRye)
        // @ProtobufIncoming = Config (Magic - auto-configures return-class)
        @Incoming("test-service-events-in")
        @ProtobufIncoming(value = "test-service-events-in", properties = { "topic=test-service-events", "auto.offset.reset=earliest" })
        public void consume(ServiceRegistered msg) {
            messages.add(msg);
        }

        public List<ServiceRegistered> getReceivedMessages() {
            return messages;
        }
    }
}
