package ai.pipestream.dynamic.grpc;

import ai.pipestream.dynamic.grpc.client.DynamicGrpcClientFactory;
import ai.pipestream.grpc.wiremock.client.WireMockServerTestResource;
import ai.pipestream.grpc.wiremock.client.WireMockGrpcClient;
import com.github.tomakehurst.wiremock.client.WireMock;
import ai.pipestream.platform.registration.*;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static ai.pipestream.grpc.wiremock.client.WireMockGrpcClient.*;

@QuarkusTest
@QuarkusTestResource(WireMockServerTestResource.class)
class LiveRegistrationServiceTest {

    @Inject
    DynamicGrpcClientFactory factory;

    private static final String REGISTRATION_SERVICE_NAME = "registration-service";

    @ConfigProperty(name = "wiremock.url")
    String wiremockUrl;

    @org.junit.jupiter.api.BeforeEach
    void setupStork() {
        int httpPort = Integer.parseInt(wiremockUrl.substring(wiremockUrl.lastIndexOf(":") + 1));
        
        // Configure WireMock Client
        WireMock.configureFor("localhost", httpPort);
        WireMock.reset();

        if (io.smallrye.stork.Stork.getInstance() != null) io.smallrye.stork.Stork.shutdown();
        io.smallrye.stork.Stork.initialize(new io.smallrye.stork.integration.DefaultStorkInfrastructure());
        
        var params = java.util.Map.of("address-list", "localhost:" + httpPort);
        var discoveryConfig = new io.smallrye.stork.spi.config.SimpleServiceConfig.SimpleServiceDiscoveryConfig("static", params);
        io.smallrye.stork.api.ServiceDefinition definition = io.smallrye.stork.api.ServiceDefinition.of(discoveryConfig);
        io.smallrye.stork.Stork.getInstance().defineIfAbsent(REGISTRATION_SERVICE_NAME, definition);
    }

    @Test
    void testConnectToLiveRegistrationService() {
        var clientUni = factory.getPlatformRegistrationClient(REGISTRATION_SERVICE_NAME);
        var client = clientUni.await().atMost(Duration.ofSeconds(5));

        assertThat(client).isNotNull();

        var request = ServiceLookupRequest.newBuilder()
            .setServiceName("test-service")
            .build();

        // Stub GetService response
        ai.pipestream.platform.registration.ServiceDetails serviceDetails = ai.pipestream.platform.registration.ServiceDetails.newBuilder().setServiceName("test-service").setHost("localhost").setPort(1).build();
        
        WireMock.stubFor(
            grpcStubFor(ai.pipestream.platform.registration.PlatformRegistrationGrpc.SERVICE_NAME, "GetService")
                .willReturn(aGrpcResponseWith(serviceDetails))
        );
        
        var response = client.getService(request).await().atMost(Duration.ofSeconds(5));
        assertThat(response.getServiceName()).isEqualTo("test-service");
    }

    @Test
    void testRegisterServiceCall() {
        var registrationClientUni = factory.getPlatformRegistrationClient(REGISTRATION_SERVICE_NAME);

        // Stub RegisterService stream with a single COMPLETED event
        ai.pipestream.platform.registration.RegistrationEvent registrationEvent = ai.pipestream.platform.registration.RegistrationEvent.newBuilder().setEventType(ai.pipestream.platform.registration.EventType.COMPLETED).setMessage("ok").build();
        
        WireMock.stubFor(
            grpcStubFor(ai.pipestream.platform.registration.PlatformRegistrationGrpc.SERVICE_NAME, "RegisterService")
                .willReturn(aGrpcResponseWith(registrationEvent))
        );
        
        var registrationClient = registrationClientUni.await().atMost(Duration.ofSeconds(5));
        var serviceInfo = ServiceRegistrationRequest.newBuilder()
            .setServiceName("test-dynamic-grpc")
            .setHost("localhost")
            .setPort(8080)
            .setVersion("1.0.0")
            .build();
        var firstEvent = registrationClient.registerService(serviceInfo)
            .collect().first()
            .await().atMost(Duration.ofSeconds(10));
        assertThat(firstEvent.getEventType()).isEqualTo(ai.pipestream.platform.registration.EventType.COMPLETED);
    }

    @Test
    void testStorkBasedRegistrationClient() {
        var client = factory.getPlatformRegistrationClient(REGISTRATION_SERVICE_NAME).await().atMost(Duration.ofSeconds(5));
        
        ai.pipestream.platform.registration.ServiceListResponse listResponse = ai.pipestream.platform.registration.ServiceListResponse.newBuilder().setTotalCount(0).build();
        
        WireMock.stubFor(
            grpcStubFor(ai.pipestream.platform.registration.PlatformRegistrationGrpc.SERVICE_NAME, "ListServices")
                .willReturn(aGrpcResponseWith(listResponse))
        );
        
        var response = client.listServices(com.google.protobuf.Empty.newBuilder().build()).await().atMost(Duration.ofSeconds(5));
        assertThat(response.getTotalCount()).isEqualTo(0);
    }

    @Test
    void testListServicesCall() {
        var client = factory.getPlatformRegistrationClient(REGISTRATION_SERVICE_NAME).await().atMost(Duration.ofSeconds(5));
        
        ai.pipestream.platform.registration.ServiceListResponse listResponse = ai.pipestream.platform.registration.ServiceListResponse.newBuilder().setTotalCount(1).build();
        
        WireMock.stubFor(
            grpcStubFor(ai.pipestream.platform.registration.PlatformRegistrationGrpc.SERVICE_NAME, "ListServices")
                .willReturn(aGrpcResponseWith(listResponse))
        );
        
        var response = client.listServices(com.google.protobuf.Empty.newBuilder().build()).await().atMost(Duration.ofSeconds(5));
        assertThat(response.getTotalCount()).isEqualTo(1);
    }
}
