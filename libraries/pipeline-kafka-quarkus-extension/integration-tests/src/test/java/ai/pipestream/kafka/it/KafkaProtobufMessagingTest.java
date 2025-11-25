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
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test demonstrating the pipeline-messaging Quarkus extension.
 * <p>
 * This test verifies that:
 * 1. Protobuf messages can be sent to Kafka topics using both Mutiny and blocking approaches
 * 2. The extension auto-configures serializers/deserializers for protobuf types
 * 3. Messages are correctly serialized and deserialized end-to-end
 * 4. Channel names automatically map to topics using directional suffixes
 * <p>
 * This serves as a reference implementation for using the extension.
 * <p>
 * Note: The consumer classes are in src/main/java so that the extension's
 * AutoSchemaProcessor can detect them at build time and auto-configure the
 * Apicurio deserializer with the correct protobuf return class.
 */
@QuarkusTest
public class KafkaProtobufMessagingTest {

    // Mutiny (reactive) emitters - preferred approach for reactive applications
    @Inject
    @Channel("validation-warnings-producer")
    MutinyEmitter<ValidationWarning> warningEmitter;

    @Inject
    @Channel("validation-errors-producer")
    MutinyEmitter<ValidationError> errorEmitter;

    // Regular (blocking) emitters - alternative approach for simpler use cases
    @Inject
    @Channel("blocking-warnings-producer")
    Emitter<ValidationWarning> blockingWarningEmitter;

    @Inject
    @Channel("blocking-errors-producer")
    Emitter<ValidationError> blockingErrorEmitter;

    @Inject
    WarningConsumer warningConsumer;

    @Inject
    ErrorConsumer errorConsumer;

    @Inject
    BlockingWarningConsumer blockingWarningConsumer;

    @Inject
    BlockingErrorConsumer blockingErrorConsumer;

    @Test
    public void testMutinyValidationWarningRoundTrip() {
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
    public void testMutinyValidationErrorRoundTrip() {
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

    @Test
    public void testBlockingValidationWarningRoundTrip() {
        // Build a ValidationWarning protobuf message
        ValidationWarning warning = ValidationWarning.newBuilder()
                .setWarningType(ValidationWarningType.VALIDATION_WARNING_TYPE_PERFORMANCE)
                .setFieldPath("pipeline.nodes[0].config")
                .setMessage("Consider using batch processing for better throughput")
                .setWarningCode("PERF-001")
                .build();

        // Send it through Kafka using blocking emitter
        blockingWarningEmitter.send(warning);

        // Wait for the consumer to receive it
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> blockingWarningConsumer.getReceived().isDone());

        // Verify the message was correctly serialized and deserialized
        ValidationWarning received = blockingWarningConsumer.getReceived().join();
        assertNotNull(received);
        assertEquals(ValidationWarningType.VALIDATION_WARNING_TYPE_PERFORMANCE, received.getWarningType());
        assertEquals("pipeline.nodes[0].config", received.getFieldPath());
        assertEquals("Consider using batch processing for better throughput", received.getMessage());
        assertEquals("PERF-001", received.getWarningCode());
    }

    @Test
    public void testBlockingValidationErrorRoundTrip() {
        // Build a ValidationError protobuf message
        ValidationError error = ValidationError.newBuilder()
                .setErrorType(ValidationErrorType.VALIDATION_ERROR_TYPE_REQUIRED_FIELD)
                .setFieldPath("pipeline.config.name")
                .setMessage("Pipeline name is required")
                .setErrorCode("REQ-001")
                .build();

        // Send it through Kafka using blocking emitter
        blockingErrorEmitter.send(error);

        // Wait for the consumer to receive it
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> blockingErrorConsumer.getReceived().isDone());

        // Verify the message was correctly serialized and deserialized
        ValidationError received = blockingErrorConsumer.getReceived().join();
        assertNotNull(received);
        assertEquals(ValidationErrorType.VALIDATION_ERROR_TYPE_REQUIRED_FIELD, received.getErrorType());
        assertEquals("pipeline.config.name", received.getFieldPath());
        assertEquals("Pipeline name is required", received.getMessage());
        assertEquals("REQ-001", received.getErrorCode());
    }

}
