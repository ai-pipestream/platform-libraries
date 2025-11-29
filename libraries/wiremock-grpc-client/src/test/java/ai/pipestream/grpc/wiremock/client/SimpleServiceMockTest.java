package ai.pipestream.grpc.wiremock.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import com.google.protobuf.Empty;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// We need a gRPC service to test against. We can use Health from grpc-services 
// or just use a generic service name and check if we get a response (even if client throws unimplemented if no stub).
// But SimpleServiceMock uses "Ping" and "HealthCheck" methods.
// We need a stub client for these. Since we don't have a "SimpleService" proto definition in our project,
// we will test this by verifying the WireMock mapping is registered and potentially using a dynamic client or 
// just assuming if AccountManager/Connector work, the mechanism works.
//
// However, best practice is to test it. 
// SimpleServiceMock allows passing a service name.
// Let's use 'grpc.health.v1.Health' which IS in our classpath (from grpc-services).

import grpc.health.v1.HealthGrpc;
// import grpc.health.v1.HealthCheckRequest; // Not found/needed for mapping check
// import grpc.health.v1.HealthCheckResponse; // Not found/needed for mapping check

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(WireMockServerTestResource.class)
public class SimpleServiceMockTest {

    private static final Logger LOG = Logger.getLogger(SimpleServiceMockTest.class);

    @ConfigProperty(name = "wiremock.url")
    String wiremockUrl;

    private ManagedChannel channel;
    private HealthGrpc.HealthBlockingStub healthClient;
    private SimpleServiceMock simpleMock;

    @BeforeEach
    void setUp() {
        int httpPort = Integer.parseInt(wiremockUrl.substring(wiremockUrl.lastIndexOf(":") + 1));
        WireMock.configureFor("localhost", httpPort);
        WireMock.reset();

        // Use standard Health service name
        simpleMock = new SimpleServiceMock("grpc.health.v1.Health");

        channel = ManagedChannelBuilder.forAddress("localhost", httpPort)
            .usePlaintext()
            .build();
        healthClient = HealthGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Test
    void testMockHealthCheck() {
        // The SimpleServiceMock.mockHealthCheck() stubs method "HealthCheck".
        // The standard grpc.health.v1.Health service has method "Check".
        // SimpleServiceMock seems designed for a custom "SimpleService".
        // Let's adjust the test to manually stub "Check" using the same mechanism to verify it works,
        // OR assume SimpleServiceMock is for a specific internal contract.
        //
        // Let's look at SimpleServiceMock code again. It stubs "HealthCheck".
        // If we want to test it, we need a client that calls "HealthCheck".
        // Since we don't have a proto for "SimpleService", we can't easily generate a client for it here
        // without adding a proto.
        //
        // However, we can verify that the Stub is registered in WireMock.
        
        simpleMock.mockHealthCheck();
        
        var mappings = WireMock.listAllStubMappings().getMappings();
        boolean found = mappings.stream().anyMatch(m -> 
            m.getRequest().getUrl().equals("/grpc.health.v1.Health/HealthCheck")
        );
        
        assertThat("HealthCheck stub should be registered", found, is(true));
    }
}
