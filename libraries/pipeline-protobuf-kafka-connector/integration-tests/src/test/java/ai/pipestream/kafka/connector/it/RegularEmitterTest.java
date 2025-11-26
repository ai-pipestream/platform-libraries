package ai.pipestream.kafka.connector.it;

import ai.pipestream.api.annotation.ProtobufChannel;
import ai.pipestream.api.annotation.ProtobufIncoming;
import ai.pipestream.platform.registration.ServiceRegistered;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.reactive.messaging.Emitter;
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
public class RegularEmitterTest {

    // REGULAR EMITTER EXAMPLE (Imperative/Sync)
    @Inject
    @Channel("regular-emitter-out")
    @ProtobufChannel(value = "regular-emitter-out", properties = { "topic=regular-emitter-test" })
    Emitter<ServiceRegistered> emitter;

    @Inject
    RegularConsumer consumer;

    @Test
    public void testRegularEmitterProtobufMessage() {
        // Create
        ServiceRegistered message = ServiceRegistered.newBuilder()
                .setServiceName("regular-emitter-service")
                .setServiceId("regular-id-456")
                .build();

        // Send using regular Emitter (imperative/sync pattern)
        emitter.send(message);

        // Verify
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    assertThat(consumer.getReceivedMessages())
                            .isNotEmpty()
                            .last()
                            .satisfies(msg -> {
                                assertThat(msg.getServiceName()).isEqualTo("regular-emitter-service");
                                assertThat(msg.getServiceId()).isEqualTo("regular-id-456");
                            });
                });
    }

    @ApplicationScoped
    public static class RegularConsumer {

        private final List<ServiceRegistered> messages = new CopyOnWriteArrayList<>();

        @Incoming("regular-emitter-in")
        @ProtobufIncoming(value = "regular-emitter-in", properties = { "topic=regular-emitter-test", "auto.offset.reset=earliest" })
        public void consume(ServiceRegistered msg) {
            messages.add(msg);
        }

        public List<ServiceRegistered> getReceivedMessages() {
            return messages;
        }
    }
}
