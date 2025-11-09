package ai.pipestream.grpc.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.wiremock.grpc.dsl.WireMockGrpcService;
import ai.pipestream.platform.registration.PlatformRegistrationGrpc;
import ai.pipestream.platform.registration.RegistrationEvent;
import ai.pipestream.platform.registration.EventType;
import ai.pipestream.platform.registration.ServiceListResponse;
import ai.pipestream.platform.registration.ModuleListResponse;
import ai.pipestream.platform.registration.ServiceDetails;
import ai.pipestream.platform.registration.ModuleDetails;
import com.google.protobuf.Timestamp;

import static ai.pipestream.grpc.wiremock.WireMockGrpcCompat.*;

/**
 * Ready-to-use mock utilities for the Platform Registration Service.
 * 
 * IMPORTANT: WireMock gRPC does NOT support true streaming responses.
 * This mock simulates streaming by returning only the first event in the stream.
 * For testing purposes, this is sufficient to verify that the registration
 * process is initiated and the client can handle the response.
 */
public class PlatformRegistrationMock {

    private final WireMockGrpcService mockService;

    /**
     * Creates a platform registration mock for the given WireMock port.
     *
     * @param wireMockPort The port where WireMock is running
     */
    public PlatformRegistrationMock(int wireMockPort) {
        this.mockService = new WireMockGrpcService(
            new WireMock(wireMockPort), 
            PlatformRegistrationGrpc.SERVICE_NAME
        );
    }

    /**
     * Creates a platform registration mock using an existing WireMock server.
     *
     * @param wireMock The WireMock server instance
     */
    public PlatformRegistrationMock(WireMockServer wireMock) {
        this.mockService = new WireMockGrpcService(
            new WireMock(wireMock.port()), 
            PlatformRegistrationGrpc.SERVICE_NAME
        );
    }

    /**
     * Mock service registration with streaming response.
     * 
     * <p>NOTE: Due to WireMock gRPC limitations, this only returns the first event.
     * In real usage, this would stream multiple events through the phases.
     *
     * @return this instance for method chaining
     */
    public PlatformRegistrationMock mockServiceRegistration() {
        mockService.stubFor(
            method("RegisterService")
                .willReturn(message(
                    RegistrationEvent.newBuilder()
                        .setEventType(EventType.STARTED)
                        .setMessage("Service registration started - streaming simulation")
                        .build()
                ))
        );
        
        return this;
    }

    /**
     * Mock service registration that returns completion event.
     * 
     * <p>This simulates a successful registration without the full streaming.
     *
     * @return this instance for method chaining
     */
    public PlatformRegistrationMock mockServiceRegistrationCompleted() {
        mockService.stubFor(
            method("RegisterService")
                .willReturn(message(
                    RegistrationEvent.newBuilder()
                        .setEventType(EventType.COMPLETED)
                        .setMessage("Service registration completed successfully")
                        .build()
                ))
        );
        
        return this;
    }

    /**
     * Mock module registration with streaming response.
     * 
     * <p>NOTE: Due to WireMock gRPC limitations, this only returns the first event.
     * In real usage, this would stream multiple events through the phases.
     *
     * @return this instance for method chaining
     */
    public PlatformRegistrationMock mockModuleRegistration() {
        mockService.stubFor(
            method("RegisterModule")
                .willReturn(message(
                    RegistrationEvent.newBuilder()
                        .setEventType(EventType.STARTED)
                        .setMessage("Module registration started - streaming simulation")
                        .build()
                ))
        );
        
        return this;
    }

    /**
     * Mock module registration that returns completion event.
     * 
     * <p>This simulates a successful registration without the full streaming.
     *
     * @return this instance for method chaining
     */
    public PlatformRegistrationMock mockModuleRegistrationCompleted() {
        mockService.stubFor(
            method("RegisterModule")
                .willReturn(message(
                    RegistrationEvent.newBuilder()
                        .setEventType(EventType.COMPLETED)
                        .setMessage("Module registration completed successfully")
                        .build()
                ))
        );
        
        return this;
    }

    /**
     * Mock successful service listing.
     * 
     * <p>Returns a list with two example services: repository-service and account-manager.
     *
     * @return this instance for method chaining
     */
    public PlatformRegistrationMock mockListServices() {
        mockService.stubFor(
            method("ListServices")
                .willReturn(message(
                    ServiceListResponse.newBuilder()
                        .addServices(ServiceDetails.newBuilder()
                            .setServiceName("repository-service")
                            .setServiceId("repo-1")
                            .setHost("localhost")
                            .setPort(8080)
                            .setVersion("1.0.0")
                            .setIsHealthy(true)
                            .setRegisteredAt(Timestamp.getDefaultInstance())
                            .setLastHealthCheck(Timestamp.getDefaultInstance())
                            .build())
                        .addServices(ServiceDetails.newBuilder()
                            .setServiceName("account-manager")
                            .setServiceId("account-1")
                            .setHost("localhost")
                            .setPort(38105)
                            .setVersion("1.0.0")
                            .setIsHealthy(true)
                            .setRegisteredAt(Timestamp.getDefaultInstance())
                            .setLastHealthCheck(Timestamp.getDefaultInstance())
                            .build())
                        .setAsOf(Timestamp.getDefaultInstance())
                        .setTotalCount(2)
                        .build()
                ))
        );
        return this;
    }

    /**
     * Mock successful module listing.
     * 
     * <p>Returns a list with two example modules: parser and chunker.
     *
     * @return this instance for method chaining
     */
    public PlatformRegistrationMock mockListModules() {
        mockService.stubFor(
            method("ListModules")
                .willReturn(message(
                    ModuleListResponse.newBuilder()
                        .addModules(ModuleDetails.newBuilder()
                            .setModuleName("parser")
                            .setServiceId("parser-1")
                            .setHost("localhost")
                            .setPort(8081)
                            .setVersion("1.0.0")
                            .setInputFormat("text/plain")
                            .setOutputFormat("application/json")
                            .addDocumentTypes("text")
                            .setIsHealthy(true)
                            .setRegisteredAt(Timestamp.getDefaultInstance())
                            .setLastHealthCheck(Timestamp.getDefaultInstance())
                            .build())
                        .addModules(ModuleDetails.newBuilder()
                            .setModuleName("chunker")
                            .setServiceId("chunker-1")
                            .setHost("localhost")
                            .setPort(8082)
                            .setVersion("1.0.0")
                            .setInputFormat("application/json")
                            .setOutputFormat("application/json")
                            .addDocumentTypes("text")
                            .setIsHealthy(true)
                            .setRegisteredAt(Timestamp.getDefaultInstance())
                            .setLastHealthCheck(Timestamp.getDefaultInstance())
                            .build())
                        .setAsOf(Timestamp.getDefaultInstance())
                        .setTotalCount(2)
                        .build()
                ))
        );
        return this;
    }

    /**
     * Get the underlying WireMockGrpcService for advanced usage.
     *
     * @return the WireMockGrpcService instance
     */
    public WireMockGrpcService getService() {
        return mockService;
    }
}