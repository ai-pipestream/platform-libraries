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
 * Example showing how to use the MockServiceFactory in practice.
 * 
 * This demonstrates the complete workflow of how a service (like echo)
 * would interact with the platform registration service using mocks.
 */
public class MockServiceFactoryUsageExample {

    private ManagedChannel channel;
    private PlatformRegistrationGrpc.PlatformRegistrationStub stub;

    @BeforeEach
    void setUp() {
        // Start mock services - this is what you'd do in your test setup
        MockServiceFactory.startMockPlatformRegistrationService();
        
        // Create a channel to the mock service
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
        // Stop mock services - this is what you'd do in your test cleanup
        MockServiceFactory.stopMockPlatformRegistrationService();
    }

    @Test
    void testEchoServiceRegistrationWorkflow() throws InterruptedException {
        // This simulates what the echo service would do when it starts up
        
        // Step 1: Echo service registers itself as a module
        ModuleRegistrationRequest echoRegistration = ModuleRegistrationRequest.newBuilder()
            .setModuleName("echo")
            .setHost("localhost")
            .setPort(8080)
            .setVersion("1.0.0")
            .build();

        // Register the echo service
        List<RegistrationEvent> registrationEvents = new ArrayList<>();
        CountDownLatch registrationLatch = new CountDownLatch(1);
        boolean[] registrationError = {false};

        stub.registerModule(echoRegistration, new io.grpc.stub.StreamObserver<RegistrationEvent>() {
            @Override
            public void onNext(RegistrationEvent event) {
                registrationEvents.add(event);
                System.out.println("Echo registration: " + event.getEventType() + " - " + event.getMessage());
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Echo registration error: " + t.getMessage());
                registrationError[0] = true;
                registrationLatch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("Echo registration completed successfully");
                registrationLatch.countDown();
            }
        });

        // Wait for registration to complete
        assertTrue(registrationLatch.await(10, TimeUnit.SECONDS), "Echo registration should complete");
        assertFalse(registrationError[0], "Echo registration should not fail");
        assertEquals(10, registrationEvents.size(), "Should receive 10 registration events");
        
        // Verify the registration was successful
        RegistrationEvent finalEvent = registrationEvents.get(registrationEvents.size() - 1);
        assertEquals(EventType.COMPLETED, finalEvent.getEventType());
        assertTrue(finalEvent.getMessage().contains("completed successfully"));
    }

    @Test
    void testEchoServiceCanDiscoverOtherServices() throws InterruptedException {
        // This simulates what the echo service would do to discover other services
        
        // Query for registered services
        CountDownLatch servicesLatch = new CountDownLatch(1);
        boolean[] servicesError = {false};
        ServiceListResponse[] servicesResponse = {null};

        stub.listServices(Empty.getDefaultInstance(), new io.grpc.stub.StreamObserver<ServiceListResponse>() {
            @Override
            public void onNext(ServiceListResponse response) {
                servicesResponse[0] = response;
                System.out.println("Found " + response.getTotalCount() + " services");
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Services query error: " + t.getMessage());
                servicesError[0] = true;
                servicesLatch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("Services query completed");
                servicesLatch.countDown();
            }
        });

        assertTrue(servicesLatch.await(5, TimeUnit.SECONDS), "Services query should complete");
        assertFalse(servicesError[0], "Services query should not fail");
        assertNotNull(servicesResponse[0], "Should receive services response");
        assertEquals(2, servicesResponse[0].getTotalCount(), "Should find 2 services");
    }

    @Test
    void testEchoServiceCanDiscoverOtherModules() throws InterruptedException {
        // This simulates what the echo service would do to discover other modules
        
        // Query for registered modules
        CountDownLatch modulesLatch = new CountDownLatch(1);
        boolean[] modulesError = {false};
        ModuleListResponse[] modulesResponse = {null};

        stub.listModules(Empty.getDefaultInstance(), new io.grpc.stub.StreamObserver<ModuleListResponse>() {
            @Override
            public void onNext(ModuleListResponse response) {
                modulesResponse[0] = response;
                System.out.println("Found " + response.getTotalCount() + " modules");
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Modules query error: " + t.getMessage());
                modulesError[0] = true;
                modulesLatch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("Modules query completed");
                modulesLatch.countDown();
            }
        });

        assertTrue(modulesLatch.await(5, TimeUnit.SECONDS), "Modules query should complete");
        assertFalse(modulesError[0], "Modules query should not fail");
        assertNotNull(modulesResponse[0], "Should receive modules response");
        assertEquals(2, modulesResponse[0].getTotalCount(), "Should find 2 modules");
    }

    @Test
    void testMockServiceFactoryProvidesConsistentInterface() {
        // This test verifies that the MockServiceFactory provides the same interface
        // as the real platform registration service
        
        // Verify the factory is working
        assertTrue(MockServiceFactory.isMockServiceRunning(), "Mock service should be running");
        
        // Verify we can get the mock server
        DirectWireMockGrpcServer mockServer = MockServiceFactory.getMockServer();
        assertNotNull(mockServer, "Mock server should be available");
        assertTrue(mockServer.getGrpcPort() > 0, "Mock server should have a valid port");
        
        // Verify we can get stubs from the factory
        assertDoesNotThrow(() -> {
            MockServiceFactory.getPlatformRegistrationStub();
        }, "Should be able to get async stub");
        
        assertDoesNotThrow(() -> {
            MockServiceFactory.getPlatformRegistrationBlockingStub();
        }, "Should be able to get blocking stub");
    }

    @Test
    void testMultipleServicesCanUseSameMock() throws InterruptedException {
        // This test demonstrates that multiple services can use the same mock
        // (simulating a real scenario where multiple services register)
        
        // Service 1: Echo service
        ModuleRegistrationRequest echoRegistration = ModuleRegistrationRequest.newBuilder()
            .setModuleName("echo")
            .setHost("localhost")
            .setPort(8080)
            .setVersion("1.0.0")
            .build();

        // Service 2: Parser service
        ModuleRegistrationRequest parserRegistration = ModuleRegistrationRequest.newBuilder()
            .setModuleName("parser")
            .setHost("localhost")
            .setPort(8081)
            .setVersion("1.0.0")
            .build();

        // Register both services
        CountDownLatch echoLatch = new CountDownLatch(1);
        CountDownLatch parserLatch = new CountDownLatch(1);
        
        stub.registerModule(echoRegistration, new io.grpc.stub.StreamObserver<RegistrationEvent>() {
            @Override
            public void onNext(RegistrationEvent event) {
                System.out.println("Echo: " + event.getMessage());
            }
            @Override
            public void onError(Throwable t) {
                echoLatch.countDown();
            }
            @Override
            public void onCompleted() {
                echoLatch.countDown();
            }
        });

        stub.registerModule(parserRegistration, new io.grpc.stub.StreamObserver<RegistrationEvent>() {
            @Override
            public void onNext(RegistrationEvent event) {
                System.out.println("Parser: " + event.getMessage());
            }
            @Override
            public void onError(Throwable t) {
                parserLatch.countDown();
            }
            @Override
            public void onCompleted() {
                parserLatch.countDown();
            }
        });

        // Both registrations should complete successfully
        assertTrue(echoLatch.await(10, TimeUnit.SECONDS), "Echo registration should complete");
        assertTrue(parserLatch.await(10, TimeUnit.SECONDS), "Parser registration should complete");
    }
}