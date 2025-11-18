package ai.pipestream.grpc.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import ai.pipestream.repository.filesystem.upload.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for RepositoryServiceMockTestResource.
 * <p>
 * Verifies that the test resource:
 * 1. Starts WireMock correctly
 * 2. Configures Stork static discovery
 * 3. Injects WireMock server into test
 * 4. Routes gRPC calls to WireMock
 */
@QuarkusTest
@QuarkusTestResource(RepositoryServiceMockTestResource.class)
public class RepositoryServiceMockTestResourceTest {

    @InjectWireMock
    WireMockServer wireMockServer;

    private ManagedChannel channel;
    private NodeUploadServiceGrpc.NodeUploadServiceBlockingStub uploadService;

    @BeforeEach
    void setUp() {
        // Verify WireMock server is injected
        assertThat("WireMock server should be injected", wireMockServer, is(notNullValue()));
        assertThat("WireMock server should be running", wireMockServer.isRunning(), is(true));
        assertThat("WireMock server should have a valid port", wireMockServer.port(), is(greaterThan(0)));

        // Create gRPC client using Stork (which should route to WireMock)
        // Note: In a real Quarkus test, you'd use @GrpcClient, but for this test
        // we'll create a direct channel to verify the mock works
        channel = ManagedChannelBuilder.forAddress("localhost", wireMockServer.port())
            .usePlaintext()
            .build();
        uploadService = NodeUploadServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }

    @Test
    void testWireMockServerStarted() {
        assertNotNull(wireMockServer, "WireMock server should be injected");
        assertTrue(wireMockServer.isRunning(), "WireMock server should be running");
        assertTrue(wireMockServer.port() > 0, "WireMock server should have a valid port");
    }

    @Test
    void testRepositoryServiceMockWorks() {
        // Setup mock
        RepositoryServiceMock repositoryMock = new RepositoryServiceMock(wireMockServer.port());
        repositoryMock.mockInitiateUpload("test-node-123", "upload-456");

        // Call the service (directly to WireMock port)
        var response = uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("test-file.txt")
                .build()
        );

        // Verify response with Hamcrest matchers
        assertThat("Response should not be null", response, is(notNullValue()));
        assertThat("Node ID should match", response.getNodeId(), is(equalTo("test-node-123")));
        assertThat("Upload ID should match", response.getUploadId(), is(equalTo("upload-456")));
        assertThat("State should be PENDING", response.getState(), is(UploadState.UPLOAD_STATE_PENDING));
        assertThat("Created timestamp should be positive", response.getCreatedAtEpochMs(), is(greaterThan(0L)));
        assertThat("Should not be an update", response.getIsUpdate(), is(false));
    }

    @Test
    void testFailureScenarios() {
        // Setup failure mock
        RepositoryServiceMock repositoryMock = new RepositoryServiceMock(wireMockServer.port());
        repositoryMock.mockInitiateUploadNotFound("Drive not found");

        // Call should fail
        var exception = assertThrows(io.grpc.StatusRuntimeException.class, () -> uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("missing-drive")
                .setName("test-file.txt")
                .build()
        ));

        assertEquals(io.grpc.Status.Code.NOT_FOUND, exception.getStatus().getCode());
    }
}

