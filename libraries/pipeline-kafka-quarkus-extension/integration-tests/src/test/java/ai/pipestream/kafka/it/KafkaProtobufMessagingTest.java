package ai.pipestream.kafka.it;

import ai.pipestream.validation.v1.ValidationError;
import ai.pipestream.validation.v1.ValidationErrorType;
import ai.pipestream.validation.v1.ValidationWarning;
import ai.pipestream.validation.v1.ValidationWarningType;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test demonstrating the pipeline-messaging Quarkus extension.
 *
 * This test verifies that:
 * 1. Protobuf messages can be sent to Kafka topics
 * 2. The extension auto-configures serializers/deserializers for protobuf types
 * 3. Messages are correctly serialized and deserialized end-to-end
 *
 * This serves as a reference implementation for using the extension.
 */
@QuarkusTest
public class KafkaProtobufMessagingTest {

    private static final String WARNING_TOPIC = "validation-warnings-v1";
    private static final String ERROR_TOPIC = "validation-errors-v1";

    // Configure topic mappings via system properties
    // The extension reads PIPELINE_TOPIC_<CHANNEL_NAME> to map channels to topics
    static {
        // Producer channels (no "in" or "consumer" in name -> outgoing)
        System.setProperty("PIPELINE_TOPIC_WARNING_PRODUCER", WARNING_TOPIC);
        System.setProperty("PIPELINE_TOPIC_ERROR_PRODUCER", ERROR_TOPIC);
        // Consumer channels ("consumer" in name -> incoming)
        System.setProperty("PIPELINE_TOPIC_WARNING_CONSUMER", WARNING_TOPIC);
        System.setProperty("PIPELINE_TOPIC_ERROR_CONSUMER", ERROR_TOPIC);
    }

    @Inject
    @Channel("warning-producer")
    MutinyEmitter<ValidationWarning> warningEmitter;

    @Inject
    @Channel("error-producer")
    MutinyEmitter<ValidationError> errorEmitter;

    @Inject
    WarningConsumer warningConsumer;

    @Inject
    ErrorConsumer errorConsumer;

    @Test
    public void testValidationWarningRoundTrip() {
        // Build a ValidationWarning protobuf message
        ValidationWarning warning = ValidationWarning.newBuilder()
                .setWarningType(ValidationWarningType.VALIDATION_WARNING_TYPE_PERFORMANCE)
                .setFieldPath("pipeline.nodes[0].config")
                .setMessage("Consider using batch processing for better throughput")
                .setWarningCode("PERF-001")
                .build();

        // Send it through Kafka
        warningEmitter.send(warning).await().atMost(Duration.ofSeconds(10));

        // Wait for the consumer to receive it
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> warningConsumer.getReceived().isDone());

        // Verify the message was correctly serialized and deserialized
        ValidationWarning received = warningConsumer.getReceived().join();
        assertNotNull(received);
        assertEquals(ValidationWarningType.VALIDATION_WARNING_TYPE_PERFORMANCE, received.getWarningType());
        assertEquals("pipeline.nodes[0].config", received.getFieldPath());
        assertEquals("Consider using batch processing for better throughput", received.getMessage());
        assertEquals("PERF-001", received.getWarningCode());
    }

    @Test
    public void testValidationErrorRoundTrip() {
        // Build a ValidationError protobuf message
        ValidationError error = ValidationError.newBuilder()
                .setErrorType(ValidationErrorType.VALIDATION_ERROR_TYPE_REQUIRED_FIELD)
                .setFieldPath("pipeline.config.name")
                .setMessage("Pipeline name is required")
                .setErrorCode("REQ-001")
                .build();

        // Send it through Kafka
        errorEmitter.send(error).await().atMost(Duration.ofSeconds(10));

        // Wait for the consumer to receive it
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> errorConsumer.getReceived().isDone());

        // Verify the message was correctly serialized and deserialized
        ValidationError received = errorConsumer.getReceived().join();
        assertNotNull(received);
        assertEquals(ValidationErrorType.VALIDATION_ERROR_TYPE_REQUIRED_FIELD, received.getErrorType());
        assertEquals("pipeline.config.name", received.getFieldPath());
        assertEquals("Pipeline name is required", received.getMessage());
        assertEquals("REQ-001", received.getErrorCode());
    }

    @ApplicationScoped
    public static class WarningConsumer {
        private final CompletableFuture<ValidationWarning> received = new CompletableFuture<>();

        @Incoming("warning-consumer")
        public void consume(ValidationWarning msg) {
            received.complete(msg);
        }

        public CompletableFuture<ValidationWarning> getReceived() {
            return received;
        }
    }

    @ApplicationScoped
    public static class ErrorConsumer {
        private final CompletableFuture<ValidationError> received = new CompletableFuture<>();

        @Incoming("error-consumer")
        public void consume(ValidationError msg) {
            received.complete(msg);
        }

        public CompletableFuture<ValidationError> getReceived() {
            return received;
        }
    }
}
