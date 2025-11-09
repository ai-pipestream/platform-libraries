package ai.pipestream.grpc.wiremock;

import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import ai.pipestream.platform.registration.PlatformRegistrationGrpc;
import ai.pipestream.platform.registration.ServiceListResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that WireMock gRPC extension can serve responses from pure JSON mapping files,
 * using the descriptor set shipped on the classpath by grpc-stubs (META-INF/grpc/services.dsc).
 * <p>
 * The JSON stub mapping for ListServices is in:
 * src/test/resources/META-INF/mappings/platform-registration-list-services.json
 */
public class PlatformRegistrationJsonStubbingTest extends WireMockGrpcTestBase {

    private ManagedChannel channel;

    @BeforeEach
    void setUp() {
        channel = ManagedChannelBuilder.forAddress("localhost", getWireMockPort())
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
