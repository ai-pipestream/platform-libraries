package ai.pipestream.dynamic.grpc;

import ai.pipestream.dynamic.grpc.client.DynamicGrpcClientFactory;
import ai.pipestream.grpc.wiremock.client.WireMockGrpcCompat;
import ai.pipestream.grpc.wiremock.client.WireMockServerTestResource;
import ai.pipestream.platform.registration.*;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(WireMockServerTestResource.class)
class SimpleServiceRegistrationTest {

    @Inject
    DynamicGrpcClientFactory factory;

    private static final String REGISTRATION_SERVICE_NAME = "registration-service";

    @ConfigProperty(name = "wiremock.url")
    String wiremockUrl;

    @org.junit.jupiter.api.BeforeEach
    void setupStork() {
        int httpPort = Integer.parseInt(wiremockUrl.substring(wiremockUrl.lastIndexOf(":") + 1));

        if (io.smallrye.stork.Stork.getInstance() != null) io.smallrye.stork.Stork.shutdown();
        io.smallrye.stork.Stork.initialize(new io.smallrye.stork.integration.DefaultStorkInfrastructure());
        
        var params = java.util.Map.of("address-list", "localhost:" + httpPort);
        var discoveryConfig = new io.smallrye.stork.spi.config.SimpleServiceConfig.SimpleServiceDiscoveryConfig("static", params);
        io.smallrye.stork.api.ServiceDefinition definition = io.smallrye.stork.api.ServiceDefinition.of(discoveryConfig);
        io.smallrye.stork.Stork.getInstance().defineIfAbsent(REGISTRATION_SERVICE_NAME, definition);
    }

    @Test
    void testBasicServiceRegistrationConnection() {
        int httpPort = Integer.parseInt(wiremockUrl.substring(wiremockUrl.lastIndexOf(":") + 1));
        System.out.println("Testing basic ServiceRegistration connection...");

        var clientUni = factory.getPlatformRegistrationClient(REGISTRATION_SERVICE_NAME);
        var client = clientUni.await().atMost(Duration.ofSeconds(5));

        assertThat(client).isNotNull();
        System.out.println("✓ Client created successfully");

        var svc = new org.wiremock.grpc.dsl.WireMockGrpcService(new com.github.tomakehurst.wiremock.client.WireMock("localhost", httpPort), ai.pipestream.platform.registration.PlatformRegistrationGrpc.SERVICE_NAME);
        ai.pipestream.platform.registration.ServiceListResponse listResponse = ai.pipestream.platform.registration.ServiceListResponse.newBuilder().setTotalCount(0).build();
        svc.stubFor(WireMockGrpcCompat.method("ListServices").willReturn(WireMockGrpcCompat.message(
            listResponse
        )));
        var response = client.listServices(com.google.protobuf.Empty.newBuilder().build())
            .await().atMost(Duration.ofSeconds(5));
        assertThat(response.getTotalCount()).isEqualTo(0);
    }

    @Test
    void testRegisterServiceCall() {
        int httpPort = Integer.parseInt(wiremockUrl.substring(wiremockUrl.lastIndexOf(":") + 1));
        System.out.println("Testing RegisterService call...");

        var clientUni = factory.getPlatformRegistrationClient(REGISTRATION_SERVICE_NAME);
        var client = clientUni.await().atMost(Duration.ofSeconds(5));

        var serviceInfo = ServiceRegistrationRequest.newBuilder()
            .setServiceName("test-service")
            .setHost("localhost")
            .setPort(8080)
            .setVersion("1.0.0")
            .build();

        var svc = new org.wiremock.grpc.dsl.WireMockGrpcService(new com.github.tomakehurst.wiremock.client.WireMock("localhost", httpPort), ai.pipestream.platform.registration.PlatformRegistrationGrpc.SERVICE_NAME);
        ai.pipestream.platform.registration.RegistrationEvent registrationEvent = ai.pipestream.platform.registration.RegistrationEvent.newBuilder().setEventType(ai.pipestream.platform.registration.EventType.COMPLETED).setMessage("ok").build();
        svc.stubFor(WireMockGrpcCompat.method("RegisterService").willReturn(WireMockGrpcCompat.message(
            registrationEvent
        )));
        var firstEvent = client.registerService(serviceInfo)
            .collect().first()
            .await().atMost(Duration.ofSeconds(10));
        assertThat(firstEvent.getEventType()).isEqualTo(ai.pipestream.platform.registration.EventType.COMPLETED);
    }
    // Live checks removed
}
