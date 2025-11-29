package ai.pipestream.grpc.wiremock.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import ai.pipestream.platform.registration.PlatformRegistrationGrpc;
import ai.pipestream.platform.registration.RegistrationEvent;
import ai.pipestream.platform.registration.EventType;
import ai.pipestream.platform.registration.ServiceRegistrationRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the streaming gRPC server running inside the WireMock container works correctly.
 * This replaces the old DirectWireMockGrpcServerTest which ran a local server.
 */
@QuarkusTest
@QuarkusTestResource(WireMockServerTestResource.class)
public class RemoteStreamingTest {

    private static final Logger LOG = Logger.getLogger(RemoteStreamingTest.class);

    @ConfigProperty(name = "wiremock.streaming.port")
    int streamingPort;

    private ManagedChannel channel;

    @BeforeEach
    void setUp() {
        LOG.info("Connecting to Remote Streaming Server on port: " + streamingPort);
        channel = ManagedChannelBuilder.forAddress("localhost", streamingPort)
            .usePlaintext()
            .build();
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Test
    void testStreamingRegistration() throws InterruptedException {
        PlatformRegistrationGrpc.PlatformRegistrationStub stub =
            PlatformRegistrationGrpc.newStub(channel);

        List<RegistrationEvent> events = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        ServiceRegistrationRequest request = ServiceRegistrationRequest.newBuilder()
            .setServiceName("remote-streaming-test")
            .build();

        stub.registerService(request, new StreamObserver<RegistrationEvent>() {
            @Override
            public void onNext(RegistrationEvent event) {
                LOG.info("Received event: " + event.getEventType());
                events.add(event);
            }

            @Override
            public void onError(Throwable t) {
                LOG.error("Error in streaming", t);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                LOG.info("Streaming completed.");
                latch.countDown();
            }
        });

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        assertThat("Streaming call should complete within timeout", finished, is(true));
        
        // Verify we received all phases of registration from the remote server
        assertThat("Should receive 6 events", events.size(), is(equalTo(6)));
        assertThat(events.get(0).getEventType(), is(EventType.STARTED));
        assertThat(events.get(5).getEventType(), is(EventType.COMPLETED));
    }
}