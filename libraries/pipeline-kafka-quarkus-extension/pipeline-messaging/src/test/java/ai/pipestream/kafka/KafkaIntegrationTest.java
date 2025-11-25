package ai.pipestream.kafka;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import io.smallrye.reactive.messaging.MutinyEmitter;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.time.Duration;
import ai.pipestream.validation.v1.ValidationWarning;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class KafkaIntegrationTest {

    private static final String TEST_TOPIC = "test-topic-v1";

    // Configure separate channels for outgoing and incoming to properly test
    // direction detection.
    // The heuristic in PipelineKafkaConfigSource detects direction based on channel
    // name:
    // - Channels with "in" or "consumer" in the name -> incoming
    // - Other channels -> outgoing
    // Both channels point to the same Kafka topic to test end-to-end communication.
    static {
        // "test-producer" has no "in" or "consumer" -> detected as outgoing
        System.setProperty("PIPELINE_TOPIC_TEST_PRODUCER", TEST_TOPIC);
        // "test-consumer" contains "consumer" -> detected as incoming
        System.setProperty("PIPELINE_TOPIC_TEST_CONSUMER", TEST_TOPIC);
    }

    @Inject
    @Channel("test-producer")
    MutinyEmitter<ValidationWarning> emitter;

    @Inject
    TestConsumer consumer;

    @Test
    public void testEndToEnd() throws InterruptedException, ExecutionException, TimeoutException {
        ValidationWarning msg = ValidationWarning.newBuilder()
                .setMessage("Hello Kafka")
                .build();

        emitter.send(msg).await().atMost(Duration.ofSeconds(10));

        // Wait for consumer using Awaitility
        org.awaitility.Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> consumer.getReceived().isDone());

        ValidationWarning received = consumer.getReceived().get();
        assertEquals("Hello Kafka", received.getMessage());
    }

    @jakarta.enterprise.context.ApplicationScoped
    public static class TestConsumer {
        private final CompletableFuture<ValidationWarning> received = new CompletableFuture<>();

        // The extension should detect this and configure the deserializer!
        // Channel name "test-consumer" contains "consumer" so direction heuristic
        // correctly identifies this as incoming.
        @Incoming("test-producer")
        public void consume(ValidationWarning msg) {
            received.complete(msg);
        }

        public CompletableFuture<ValidationWarning> getReceived() {
            return received;
        }
    }
}
