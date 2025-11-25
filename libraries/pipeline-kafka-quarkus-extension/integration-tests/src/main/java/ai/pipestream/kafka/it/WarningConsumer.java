package ai.pipestream.kafka.it;

import ai.pipestream.validation.v1.ValidationWarning;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka consumer for ValidationWarning protobuf messages.
 * <p>
 * This class is in main sources (not test sources) so that the pipeline-messaging
 * extension's AutoSchemaProcessor can detect the @Incoming annotation at build time
 * and auto-configure the Apicurio deserializer with the correct return class.
 */
@ApplicationScoped
public class WarningConsumer {
    private final CompletableFuture<ValidationWarning> received = new CompletableFuture<>();

    @Incoming("warning-consumer")
    public void consume(ValidationWarning msg) {
        received.complete(msg);
    }

    public CompletableFuture<ValidationWarning> getReceived() {
        return received;
    }
}
