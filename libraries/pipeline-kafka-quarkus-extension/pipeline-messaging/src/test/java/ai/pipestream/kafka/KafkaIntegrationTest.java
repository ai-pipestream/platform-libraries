package ai.pipestream.kafka;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import ai.pipestream.validation.v1.ValidationWarning;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class KafkaIntegrationTest {

    // We need to simulate the env var for the topic
    static {
        System.setProperty("PIPELINE_TOPIC_TEST_TOPIC", "test-topic-v1");
    }

    @Inject
    @Channel("test-topic")
    Emitter<ValidationWarning> emitter;

    // We need a consumer to verify the message
    // Since we can't easily dynamically add @Incoming methods in a test class without it being a bean,
    // we might need a separate bean or use a mock.
    // But we want to test the "Zero Config" aspect which infers from @Incoming.
    // So we should define a static inner class or a separate class bean.
    
    @Inject
    TestConsumer consumer;

    @Test
    public void testEndToEnd() throws InterruptedException, ExecutionException, TimeoutException {
        ValidationWarning msg = ValidationWarning.newBuilder()
            .setMessage("Hello Kafka")
            .build();

        emitter.send(msg).toCompletableFuture().get(10, TimeUnit.SECONDS);

        // Wait for consumer
        ValidationWarning received = consumer.getReceived().get(10, TimeUnit.SECONDS);
        assertEquals("Hello Kafka", received.getMessage());
    }
    
    @jakarta.enterprise.context.ApplicationScoped
    public static class TestConsumer {
        private final CompletableFuture<ValidationWarning> received = new CompletableFuture<>();

        // The extension should detect this and configure the deserializer!
        @Incoming("test-topic")
        public void consume(ValidationWarning msg) {
            received.complete(msg);
        }

        public CompletableFuture<ValidationWarning> getReceived() {
            return received;
        }
    }
}
