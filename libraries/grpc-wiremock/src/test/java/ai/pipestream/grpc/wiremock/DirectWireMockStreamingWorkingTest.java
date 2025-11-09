package ai.pipestream.grpc.wiremock;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import ai.pipestream.platform.registration.PlatformRegistrationGrpc;
import ai.pipestream.platform.registration.RegistrationEvent;
import ai.pipestream.platform.registration.EventType;
import ai.pipestream.platform.registration.ServiceRegistrationRequest;
import ai.pipestream.platform.registration.ModuleRegistrationRequest;
import ai.pipestream.platform.registration.ServiceListResponse;
import ai.pipestream.platform.registration.ModuleListResponse;
import com.google.protobuf.Empty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that demonstrates using WireMock directly with a custom gRPC server
 * to properly handle streaming responses.
 * 
 * This approach bypasses the WireMock gRPC extension's limitations
 * and gives us full control over streaming behavior.
 */
public class DirectWireMockStreamingWorkingTest {

    private DirectWireMockGrpcServer server;
    private ManagedChannel channel;
    private PlatformRegistrationGrpc.PlatformRegistrationStub stub;

    @BeforeEach
    void setUp() throws IOException {
        // Start the direct WireMock gRPC server with dynamic port
        server = new DirectWireMockGrpcServer(0);
        server.start();
        
        // Create gRPC channel and stub
        channel = ManagedChannelBuilder.forAddress("localhost", server.getGrpcPort())
            .usePlaintext()
            .build();
        stub = PlatformRegistrationGrpc.newStub(channel);
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
    void testServiceRegistrationStreaming() throws InterruptedException {
        // Create request
        ServiceRegistrationRequest request = ServiceRegistrationRequest.newBuilder()
            .setServiceName("test-service")
            .setHost("localhost")
            .setPort(8080)
            .setVersion("1.0.0")
            .build();

        // Test the streaming call
        List<RegistrationEvent> receivedEvents = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] errorReceived = {false};

        stub.registerService(request, new StreamObserver<RegistrationEvent>() {
            @Override
            public void onNext(RegistrationEvent event) {
                receivedEvents.add(event);
                System.out.println("Received event: " + event.getEventType() + " - " + event.getMessage());
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Error: " + t.getMessage());
                errorReceived[0] = true;
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("Stream completed");
                latch.countDown();
            }
        });

        // Wait for completion
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Stream should complete within 10 seconds");
        
        // Verify we received all the events
        assertFalse(errorReceived[0], "Should not receive an error");
        assertEquals(6, receivedEvents.size(), "Should receive 6 events for service registration");
        
        // Verify the events are in the correct order
        assertEquals(EventType.STARTED, receivedEvents.get(0).getEventType());
        assertEquals(EventType.VALIDATED, receivedEvents.get(1).getEventType());
        assertEquals(EventType.CONSUL_REGISTERED, receivedEvents.get(2).getEventType());
        assertEquals(EventType.HEALTH_CHECK_CONFIGURED, receivedEvents.get(3).getEventType());
        assertEquals(EventType.CONSUL_HEALTHY, receivedEvents.get(4).getEventType());
        assertEquals(EventType.COMPLETED, receivedEvents.get(5).getEventType());
    }

    @Test
    void testModuleRegistrationStreaming() throws InterruptedException {
        // Create request
        ModuleRegistrationRequest request = ModuleRegistrationRequest.newBuilder()
            .setModuleName("test-module")
            .setHost("localhost")
            .setPort(8081)
            .setVersion("1.0.0")
            .build();

        // Test the streaming call
        List<RegistrationEvent> receivedEvents = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] errorReceived = {false};

        stub.registerModule(request, new StreamObserver<RegistrationEvent>() {
            @Override
            public void onNext(RegistrationEvent event) {
                receivedEvents.add(event);
                System.out.println("Received event: " + event.getEventType() + " - " + event.getMessage());
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Error: " + t.getMessage());
                errorReceived[0] = true;
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("Stream completed");
                latch.countDown();
            }
        });

        // Wait for completion
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Stream should complete within 10 seconds");
        
        // Verify we received all the events
        assertFalse(errorReceived[0], "Should not receive an error");
        assertEquals(10, receivedEvents.size(), "Should receive 10 events for module registration");
    }

    @Test
    void testUnaryCallsWork() throws InterruptedException {
        // Test unary call - this should work perfectly
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] successReceived = {false};
        ServiceListResponse[] responseHolder = {null};

        stub.listServices(
            Empty.getDefaultInstance(),
            new StreamObserver<ServiceListResponse>() {
                @Override
                public void onNext(ServiceListResponse response) {
                    System.out.println("Received " + response.getTotalCount() + " services");
                    responseHolder[0] = response;
                    successReceived[0] = true;
                }

                @Override
                public void onError(Throwable t) {
                    System.err.println("Error: " + t.getMessage());
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    System.out.println("Unary call completed successfully");
                    latch.countDown();
                }
            }
        );

        // Wait for completion
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Unary call should complete within 5 seconds");
        assertTrue(successReceived[0], "Unary call should succeed");
        assertNotNull(responseHolder[0], "Should receive a response");
        assertEquals(2, responseHolder[0].getTotalCount(), "Should have 2 services");
    }

    @Test
    void testModuleListWorks() throws InterruptedException {
        // Test module list call
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] successReceived = {false};
        ModuleListResponse[] responseHolder = {null};

        stub.listModules(
            Empty.getDefaultInstance(),
            new StreamObserver<ModuleListResponse>() {
                @Override
                public void onNext(ModuleListResponse response) {
                    System.out.println("Received " + response.getTotalCount() + " modules");
                    responseHolder[0] = response;
                    successReceived[0] = true;
                }

                @Override
                public void onError(Throwable t) {
                    System.err.println("Error: " + t.getMessage());
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    System.out.println("Module list call completed successfully");
                    latch.countDown();
                }
            }
        );

        // Wait for completion
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Module list call should complete within 5 seconds");
        assertTrue(successReceived[0], "Module list call should succeed");
        assertNotNull(responseHolder[0], "Should receive a response");
        assertEquals(2, responseHolder[0].getTotalCount(), "Should have 2 modules");
    }
}