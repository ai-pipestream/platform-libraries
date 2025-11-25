package ai.pipestream.kafka.it;

import ai.pipestream.validation.v1.ValidationError;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Blocking consumer for ValidationError protobuf messages.
 * <p>
 * This demonstrates the alternative Emitter approach using CompletionStage
 * instead of the reactive Mutiny approach.
 */
@ApplicationScoped
public class BlockingErrorConsumer {
    private final CompletableFuture<ValidationError> received = new CompletableFuture<>();

    @Incoming("blocking-errors-consumer")
    public CompletionStage<Void> consume(ValidationError msg) {
        received.complete(msg);
        return CompletableFuture.completedStage(null);
    }

    public CompletableFuture<ValidationError> getReceived() {
        return received;
    }
}
