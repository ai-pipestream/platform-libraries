package ai.pipestream.grpc.wiremock;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import ai.pipestream.platform.registration.PlatformRegistrationGrpc;
import ai.pipestream.platform.registration.RegistrationEvent;
import ai.pipestream.platform.registration.EventType;
import ai.pipestream.platform.registration.ModuleRegistrationRequest;
import ai.pipestream.platform.registration.ServiceListResponse;
import ai.pipestream.platform.registration.ModuleListResponse;
import com.google.protobuf.Empty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that demonstrates how to use the MockServiceFactory with the echo service.
 * 
 * This shows how the echo service would interact with the platform registration service
 * when using mock services instead of the real implementation.
 */
public class EchoServiceWithMockRegistrationTest {

    private ManagedChannel channel;
    private PlatformRegistrationGrpc.PlatformRegistrationStub stub;

    @BeforeEach
    void setUp() {
        // Start the mock platform registration service
        MockServiceFactory.startMockPlatformRegistrationService();
        
        // Create gRPC channel and stub
        channel = ManagedChannelBuilder.forAddress("localhost", MockServiceFactory.getMockServer().getGrpcPort())
            .usePlaintext()
            .build();
        stub = PlatformRegistrationGrpc.newStub(channel);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdown();
        }
        // Stop the mock services
        MockServiceFactory.stopMockPlatformRegistrationService();
    }

    @Test
    void testEchoServiceRegistrationFlow() throws InterruptedException {
        // This test simulates what would happen when the echo service registers itself
        // with the platform registration service
        
        // Step 1: Echo service calls registerModule
        ModuleRegistrationRequest echoRegistration = ModuleRegistrationRequest.newBuilder()
            .setModuleName("echo")
            .setHost("localhost")
            .setPort(8080)
            .setVersion("1.0.0")
            .build();

        // Test the streaming registration call
        List<RegistrationEvent> receivedEvents = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] errorReceived = {false};

        stub.registerModule(echoRegistration, new io.grpc.stub.StreamObserver<RegistrationEvent>() {
            @Override
            public void onNext(RegistrationEvent event) {
                receivedEvents.add(event);
                System.out.println("Echo registration event: " + event.getEventType() + " - " + event.getMessage());
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Echo registration error: " + t.getMessage());
                errorReceived[0] = true;
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("Echo registration completed");
                latch.countDown();
            }
        });

        // Wait for completion
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Echo registration should complete within 10 seconds");
        
        // Verify the registration was successful
        assertFalse(errorReceived[0], "Echo registration should not fail");
        assertEquals(10, receivedEvents.size(), "Should receive 10 events for module registration");
        
        // Verify the final event indicates success
        RegistrationEvent finalEvent = receivedEvents.get(receivedEvents.size() - 1);
        assertEquals(EventType.COMPLETED, finalEvent.getEventType());
        assertTrue(finalEvent.getMessage().contains("completed successfully"));
    }

    @Test
    void testEchoServiceCanQueryRegisteredModules() throws InterruptedException {
        // This test simulates the echo service querying what modules are registered
        
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] successReceived = {false};
        ModuleListResponse[] responseHolder = {null};

        stub.listModules(
            Empty.getDefaultInstance(),
            new io.grpc.stub.StreamObserver<ModuleListResponse>() {
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
                    System.out.println("Module list query completed");
                    latch.countDown();
                }
            }
        );

        // Wait for completion
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Module list query should complete within 5 seconds");
        assertTrue(successReceived[0], "Module list query should succeed");
        assertNotNull(responseHolder[0], "Should receive a response");
        assertEquals(2, responseHolder[0].getTotalCount(), "Should have 2 modules registered");
    }

    @Test
    void testEchoServiceCanQueryRegisteredServices() throws InterruptedException {
        // This test simulates the echo service querying what services are registered
        
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] successReceived = {false};
        ServiceListResponse[] responseHolder = {null};

        stub.listServices(
            Empty.getDefaultInstance(),
            new io.grpc.stub.StreamObserver<ServiceListResponse>() {
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
                    System.out.println("Service list query completed");
                    latch.countDown();
                }
            }
        );

        // Wait for completion
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Service list query should complete within 5 seconds");
        assertTrue(successReceived[0], "Service list query should succeed");
        assertNotNull(responseHolder[0], "Should receive a response");
        assertEquals(2, responseHolder[0].getTotalCount(), "Should have 2 services registered");
    }

    @Test
    void testMockServiceFactoryIntegration() {
        // Test that the MockServiceFactory works correctly
        assertTrue(MockServiceFactory.isMockServiceRunning(), "Mock service should be running");
        
        // Test that we can get the mock server
        DirectWireMockGrpcServer mockServer = MockServiceFactory.getMockServer();
        assertNotNull(mockServer, "Mock server should not be null");
        assertTrue(mockServer.getGrpcPort() > 0, "Mock server should have a valid port");
        
        // Test that we can get the stub from the factory
        PlatformRegistrationGrpc.PlatformRegistrationStub factoryStub = MockServiceFactory.getPlatformRegistrationStub();
        assertNotNull(factoryStub, "Factory stub should not be null");
    }
}