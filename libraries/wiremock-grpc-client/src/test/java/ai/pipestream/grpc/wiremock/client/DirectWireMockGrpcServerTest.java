package ai.pipestream.grpc.wiremock.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import ai.pipestream.platform.registration.PlatformRegistrationGrpc;
import ai.pipestream.platform.registration.RegistrationEvent;
import ai.pipestream.platform.registration.EventType;
import ai.pipestream.platform.registration.ServiceRegistrationRequest;
import org.jboss.logging.Logger; // Import JBoss Logger
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Disabled;

/**
 * Test that demonstrates using DirectWireMockGrpcServer for streaming responses.
 * This approach bypasses the WireMock gRPC extension's limitations (unary only)
 * by running a real gRPC server that implements the streaming logic.
 */
@Disabled("Disabled due to Protobuf version conflict (Edition support) in test runtime vs WireMock dependencies")
public class DirectWireMockGrpcServerTest {

    private static final Logger LOG = Logger.getLogger(DirectWireMockGrpcServerTest.class); // Initialize Logger

    private DirectWireMockGrpcServer server;
    private ManagedChannel channel;

    @BeforeEach
    void setUp() throws IOException {
        // Start the direct WireMock gRPC server with dynamic port (0) for internal WireMock
        server = new DirectWireMockGrpcServer(0);
        server.start();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdown();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void testStreamingRegistration() throws InterruptedException {
        // Connect to the custom gRPC server port (not WireMock's port)
        int grpcPort = server.getGrpcPort();
        LOG.info("Connecting to DirectWireMockGrpcServer on port: " + grpcPort); // Use LOG.info
        
        channel = ManagedChannelBuilder
            .forAddress("localhost", grpcPort)
            .usePlaintext()
            .build();

        PlatformRegistrationGrpc.PlatformRegistrationStub stub =
            PlatformRegistrationGrpc.newStub(channel);

        List<RegistrationEvent> events = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        ServiceRegistrationRequest request = ServiceRegistrationRequest.newBuilder()
            .setServiceName("streaming-test-service")
            .build();

        stub.registerService(request, new StreamObserver<RegistrationEvent>() {
            @Override
            public void onNext(RegistrationEvent event) {
                LOG.info("Received event: " + event.getEventType()); // Use LOG.info
                events.add(event);
            }

            @Override
            public void onError(Throwable t) {
                LOG.error("Error in streaming: " + t.getMessage(), t); // Use LOG.error
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                LOG.info("Streaming completed."); // Use LOG.info
                latch.countDown();
            }
        });

        boolean finished = latch.await(15, TimeUnit.SECONDS);
        assertThat("Streaming call should complete within timeout", finished, is(true)); // Beefed up assertion
        
        // Verify we received all phases of registration
        assertThat("Should receive 6 events for registration flow", events.size(), is(equalTo(6)));
        assertThat(events.get(0).getEventType(), is(EventType.STARTED));
        assertThat(events.get(1).getEventType(), is(EventType.VALIDATED));
        assertThat(events.get(2).getEventType(), is(EventType.CONSUL_REGISTERED));
        assertThat(events.get(3).getEventType(), is(EventType.HEALTH_CHECK_CONFIGURED));
        assertThat(events.get(4).getEventType(), is(EventType.CONSUL_HEALTHY));
        assertThat(events.get(5).getEventType(), is(EventType.COMPLETED));
    }
}
