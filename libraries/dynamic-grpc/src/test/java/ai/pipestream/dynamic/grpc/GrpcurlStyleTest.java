package ai.pipestream.dynamic.grpc;

import ai.pipestream.dynamic.grpc.client.DynamicGrpcClientFactory;
import ai.pipestream.grpc.wiremock.InjectWireMock;
import ai.pipestream.grpc.wiremock.WireMockGrpcCompat;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(LocalWireMockGrpcTestResource.class)
class GrpcurlStyleTest {

    @Inject
    DynamicGrpcClientFactory factory;

    private static final String REGISTRATION_SERVICE_NAME = "registration-service";

    @InjectWireMock
    com.github.tomakehurst.wiremock.WireMockServer wireMock;

    @org.junit.jupiter.api.BeforeEach
    void setupStork() {
        if (io.smallrye.stork.Stork.getInstance() != null) io.smallrye.stork.Stork.shutdown();
        io.smallrye.stork.Stork.initialize(new io.smallrye.stork.integration.DefaultStorkInfrastructure());
        int port = wireMock.port();
        var params = java.util.Map.of("address-list", "127.0.0.1:" + port);
        var discoveryConfig = new io.smallrye.stork.spi.config.SimpleServiceConfig.SimpleServiceDiscoveryConfig("static", params);
        io.smallrye.stork.api.ServiceDefinition definition = io.smallrye.stork.api.ServiceDefinition.of(discoveryConfig);
        io.smallrye.stork.Stork.getInstance().defineIfAbsent(REGISTRATION_SERVICE_NAME, definition);

        // Stub a simple ListServices response
        var svc = new org.wiremock.grpc.dsl.WireMockGrpcService(new com.github.tomakehurst.wiremock.client.WireMock(port), ai.pipestream.platform.registration.PlatformRegistrationGrpc.SERVICE_NAME);
        ai.pipestream.platform.registration.ServiceListResponse listResponse = ai.pipestream.platform.registration.ServiceListResponse.newBuilder().setTotalCount(0).build();
        svc.stubFor(WireMockGrpcCompat.method("ListServices").willReturn(WireMockGrpcCompat.message(
            listResponse
        )));
    }

    @Test
    void testDirectConnectionLikeGrpcurl() {
        System.out.println("Testing direct connection to localhost:39101 like grpcurl...");

        var clientUni = factory.getPlatformRegistrationClient(REGISTRATION_SERVICE_NAME);
        var client = clientUni.await().atMost(Duration.ofSeconds(5));

        assertThat(client).isNotNull();
        System.out.println("✓ Client created");
        System.out.println("Client type: " + client.getClass().getName());
        System.out.println("✓ Got client, connection should work");
    }

    // Live test removed in favor of WireMock-defined service
}
