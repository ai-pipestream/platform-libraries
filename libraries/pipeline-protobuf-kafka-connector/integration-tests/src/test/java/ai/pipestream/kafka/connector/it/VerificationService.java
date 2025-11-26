package ai.pipestream.kafka.connector.it;

import ai.pipestream.api.annotation.ProtobufChannel;
import ai.pipestream.api.annotation.ProtobufIncoming;
import ai.pipestream.platform.registration.RegistrationEvent;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class VerificationService {

    // 1. CDI Injection (Needs @Channel to find the bean)
    // 2. Config Injection (Needs @ProtobufChannel to build the config)
    @Inject
    @Channel("test-out")
    @ProtobufChannel(value="test-out", properties={"acks=1"})
    MutinyEmitter<RegistrationEvent> emitter;

    // Double annotation pattern:
    // @Incoming = Wiring (Standard SmallRye)
    // @ProtobufIncoming = Config (Magic - auto-configures return-class)
    @Incoming("test-in")
    @ProtobufIncoming("test-in")
    public void consume(RegistrationEvent event) {
        System.out.println("Received: " + event);
    }
}
