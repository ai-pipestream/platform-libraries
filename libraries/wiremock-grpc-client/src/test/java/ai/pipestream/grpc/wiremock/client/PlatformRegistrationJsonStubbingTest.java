package ai.pipestream.grpc.wiremock.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import ai.pipestream.platform.registration.PlatformRegistrationGrpc;
import ai.pipestream.platform.registration.ServiceListResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that we can manually load JSON mapping files and push them to the containerized WireMock server.
 * This replaces the automatic "META-INF/mappings" scanning which works for embedded but not easily for containerized
 * without complex volume mounts.
 */
@QuarkusTest
@QuarkusTestResource(WireMockServerTestResource.class)
public class PlatformRegistrationJsonStubbingTest {

    @ConfigProperty(name = "wiremock.url")
    String wiremockUrl;

    private ManagedChannel channel;

    @BeforeEach
    void setUp() throws IOException {
        int httpPort = Integer.parseInt(wiremockUrl.substring(wiremockUrl.lastIndexOf(":") + 1));
        WireMock.configureFor("localhost", httpPort);
        WireMock.reset();

        // Manually load the JSON mapping from the test resources
        String mappingJson;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("META-INF/mappings/platform-registration-list-services.json")) {
            if (is == null) throw new RuntimeException("Mapping file not found in test resources");
            mappingJson = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Push it to the WireMock server using an instance client
        // StubMapping.buildFrom(json) parses the JSON into a mapping object
        new WireMock("localhost", httpPort).register(StubMapping.buildFrom(mappingJson));

        channel = ManagedChannelBuilder.forAddress("localhost", httpPort)
                .usePlaintext()
                .build();
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Test
    void listServices_fromJsonMapping_shouldReturnStubbedResponse() {
        PlatformRegistrationGrpc.PlatformRegistrationBlockingStub stub =
                PlatformRegistrationGrpc.newBlockingStub(channel);

        ServiceListResponse resp = stub.listServices(Empty.getDefaultInstance());

        assertNotNull(resp);
        assertEquals(1, resp.getTotalCount());
        assertEquals(1, resp.getServicesCount());
        assertEquals("repository-service", resp.getServices(0).getServiceName());
        assertEquals(8080, resp.getServices(0).getPort());
    }
}