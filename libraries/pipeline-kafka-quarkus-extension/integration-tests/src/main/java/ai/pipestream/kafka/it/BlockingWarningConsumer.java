package ai.pipestream.kafka.it;

import ai.pipestream.validation.v1.ValidationWarning;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Blocking consumer for ValidationWarning protobuf messages.
 * <p>
 * This demonstrates the alternative Emitter approach using CompletionStage
 * instead of the reactive Mutiny approach.
 */
@ApplicationScoped
public class BlockingWarningConsumer {
    private final CompletableFuture<ValidationWarning> received = new CompletableFuture<>();

    @Incoming("blocking-warnings-consumer")
    public CompletionStage<Void> consume(ValidationWarning msg) {
        received.complete(msg);
        return CompletableFuture.completedStage(null);
    }

    public CompletableFuture<ValidationWarning> getReceived() {
        return received;
    }
}
