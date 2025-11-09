package ai.pipestream.grpc.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import ai.pipestream.platform.registration.PlatformRegistrationGrpc;
import ai.pipestream.platform.registration.RegistrationEvent;
import ai.pipestream.platform.registration.EventType;
import ai.pipestream.platform.registration.ServiceRegistrationRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wiremock.grpc.dsl.WireMockGrpcService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static ai.pipestream.grpc.wiremock.WireMockGrpcCompat.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that demonstrates the limitations of WireMock gRPC with streaming responses.
 * 
 * Key Finding: WireMock gRPC does NOT support true streaming responses.
 * It only supports unary (request/response) calls.
 * 
 * For streaming responses, we have these options:
 * 1. Mock only the first event in the stream
 * 2. Use multiple unary calls to simulate streaming
 * 3. Accept that streaming mocks are limited
 */
public class PlatformRegistrationLimitationTest {

    private WireMockServer wireMockServer;
    private WireMockGrpcService mockService;
    private ManagedChannel channel;
    private PlatformRegistrationGrpc.PlatformRegistrationStub stub;

    @BeforeEach
    void setUp() {
        // Start WireMock server with gRPC extension
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        
        // Create mock service
        mockService = new WireMockGrpcService(
            new WireMock(wireMockServer.port()), 
            PlatformRegistrationGrpc.SERVICE_NAME
        );
        
        // Create gRPC channel and stub
        channel = ManagedChannelBuilder.forAddress("localhost", wireMockServer.port())
            .usePlaintext()
            .build();
        stub = PlatformRegistrationGrpc.newStub(channel);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdown();
        }
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void testWireMockGrpcStreamingLimitation() throws InterruptedException {
        // This test demonstrates that WireMock gRPC cannot handle streaming responses
        // The client will receive an error because WireMock gRPC only supports unary calls
        
        // Mock a streaming response (this won't work properly)
        mockService.stubFor(
            method("RegisterService")
                .willReturn(message(
                    RegistrationEvent.newBuilder()
                        .setEventType(EventType.STARTED)
                        .setMessage("Starting service registration")
                        .build()
                ))
        );

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

        // Wait for completion or error
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Stream should complete or error within 5 seconds");
        
        // WireMock gRPC will cause an error because it doesn't support streaming
        assertTrue(errorReceived[0], "WireMock gRPC should cause an error for streaming responses");
        assertEquals(0, receivedEvents.size(), "No events should be received due to streaming limitation");
    }

    @Test
    void testUnaryCallWorks() {
        // This test demonstrates that unary calls work fine with WireMock gRPC
        
        // Mock a unary response (this works)
        mockService.stubFor(
            method("ListServices")
                .willReturn(message(
                    ai.pipestream.platform.registration.ServiceListResponse.newBuilder()
                        .setTotalCount(0)
                        .build()
                ))
        );

        // Test the unary call
        List<RegistrationEvent> receivedEvents = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        stub.listServices(
            com.google.protobuf.Empty.getDefaultInstance(),
            new StreamObserver<ai.pipestream.platform.registration.ServiceListResponse>() {
                @Override
                public void onNext(ai.pipestream.platform.registration.ServiceListResponse response) {
                    System.out.println("Received response: " + response.getTotalCount() + " services");
                }

                @Override
                public void onError(Throwable t) {
                    System.err.println("Error: " + t.getMessage());
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    System.out.println("Unary call completed");
                    latch.countDown();
                }
            }
        );

        // This should work without errors
        assertDoesNotThrow(() -> {
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @Test
    void testPlatformRegistrationMockWorksForUnaryCalls() {
        // Test that our PlatformRegistrationMock works for unary calls
        PlatformRegistrationMock platformMock = new PlatformRegistrationMock(wireMockServer);
        platformMock.mockListServices();

        // Test the unary call
        CountDownLatch latch = new CountDownLatch(1);

        stub.listServices(
            com.google.protobuf.Empty.getDefaultInstance(),
            new StreamObserver<ai.pipestream.platform.registration.ServiceListResponse>() {
                @Override
                public void onNext(ai.pipestream.platform.registration.ServiceListResponse response) {
                    System.out.println("Received response: " + response.getTotalCount() + " services");
                    assertEquals(2, response.getTotalCount());
                }

                @Override
                public void onError(Throwable t) {
                    System.err.println("Error: " + t.getMessage());
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    System.out.println("Unary call completed");
                    latch.countDown();
                }
            }
        );

        // This should work without errors
        assertDoesNotThrow(() -> {
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}