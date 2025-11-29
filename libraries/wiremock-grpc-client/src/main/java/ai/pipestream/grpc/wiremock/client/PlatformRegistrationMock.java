package ai.pipestream.grpc.wiremock.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import ai.pipestream.platform.registration.RegistrationEvent;
import ai.pipestream.platform.registration.EventType;
import ai.pipestream.platform.registration.ServiceListResponse;
import ai.pipestream.platform.registration.ModuleListResponse;
import ai.pipestream.platform.registration.ServiceDetails;
import ai.pipestream.platform.registration.ModuleDetails;
import com.google.protobuf.Timestamp;

import static ai.pipestream.grpc.wiremock.client.WireMockGrpcClient.*;

/**
 * Ready-to-use mock utilities for the Platform Registration Service.
 * 
 * IMPORTANT: WireMock gRPC does NOT support true streaming responses (response streaming).
 * This mock helper configures WireMock to return a single event (unary style) which is
 * typically sufficient for simple integration tests.
 * 
 * For true streaming tests, use DirectWireMockGrpcServer.
 */
public class PlatformRegistrationMock {

    private static final String SERVICE_NAME = "ai.pipestream.platform.registration.PlatformRegistration";

    public PlatformRegistrationMock() {
        // No-op constructor
    }

    /**
     * Mock service registration with a single response (simulating start).
     */
    public PlatformRegistrationMock mockServiceRegistration() {
        WireMock.stubFor(
            grpcStubFor(SERVICE_NAME, "RegisterService")
                .willReturn(aGrpcResponseWith(
                    RegistrationEvent.newBuilder()
                        .setEventType(EventType.STARTED)
                        .setMessage("Service registration started - streaming simulation")
                        .build()
                ))
        );
        return this;
    }

    /**
     * Mock service registration that returns completion event immediately.
     */
    public PlatformRegistrationMock mockServiceRegistrationCompleted() {
        WireMock.stubFor(
            grpcStubFor(SERVICE_NAME, "RegisterService")
                .willReturn(aGrpcResponseWith(
                    RegistrationEvent.newBuilder()
                        .setEventType(EventType.COMPLETED)
                        .setMessage("Service registration completed successfully")
                        .build()
                ))
        );
        return this;
    }

    /**
     * Mock module registration with a single response (simulating start).
     */
    public PlatformRegistrationMock mockModuleRegistration() {
        WireMock.stubFor(
            grpcStubFor(SERVICE_NAME, "RegisterModule")
                .willReturn(aGrpcResponseWith(
                    RegistrationEvent.newBuilder()
                        .setEventType(EventType.STARTED)
                        .setMessage("Module registration started - streaming simulation")
                        .build()
                ))
        );
        return this;
    }

    /**
     * Mock module registration that returns completion event immediately.
     */
    public PlatformRegistrationMock mockModuleRegistrationCompleted() {
        WireMock.stubFor(
            grpcStubFor(SERVICE_NAME, "RegisterModule")
                .willReturn(aGrpcResponseWith(
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
     */
    public PlatformRegistrationMock mockListServices() {
        WireMock.stubFor(
            grpcStubFor(SERVICE_NAME, "ListServices")
                .willReturn(aGrpcResponseWith(
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
     */
    public PlatformRegistrationMock mockListModules() {
        WireMock.stubFor(
            grpcStubFor(SERVICE_NAME, "ListModules")
                .willReturn(aGrpcResponseWith(
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
}