package ai.pipestream.grpc.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import ai.pipestream.repository.filesystem.upload.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RepositoryServiceMock to verify the mock stubs work correctly.
 * <p>
 * This validates the mock framework before using it in other services.
 */
public class RepositoryServiceMockTest {

    private WireMockServer wireMockServer;
    private ManagedChannel channel;
    private NodeUploadServiceGrpc.NodeUploadServiceBlockingStub uploadService;
    private RepositoryServiceMock repositoryServiceMock;

    @BeforeEach
    void setUp() {
        // Start WireMock with gRPC extension
        wireMockServer = new WireMockServer(
            com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig()
                .dynamicPort()
                .usingFilesUnderClasspath("META-INF")
                .extensions(new org.wiremock.grpc.GrpcExtensionFactory())
        );
        wireMockServer.start();

        // Create RepositoryServiceMock
        repositoryServiceMock = new RepositoryServiceMock(wireMockServer.port());

        // Create gRPC client
        channel = ManagedChannelBuilder.forAddress("localhost", wireMockServer.port())
            .usePlaintext()
            .build();
        uploadService = NodeUploadServiceGrpc.newBlockingStub(channel);
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
    void testMockInitiateUpload_Success() {
        // Setup mock
        repositoryServiceMock.mockInitiateUpload("test-node-123", "upload-456");

        // Call
        var response = uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("test-file.txt")
                .build()
        );

        // Verify with Hamcrest matchers
        assertThat("Node ID should match", response.getNodeId(), is(equalTo("test-node-123")));
        assertThat("Upload ID should match", response.getUploadId(), is(equalTo("upload-456")));
        assertThat("State should be PENDING", response.getState(), is(UploadState.UPLOAD_STATE_PENDING));
        assertThat("Should not be an update", response.getIsUpdate(), is(false));
        assertThat("Created timestamp should be positive", response.getCreatedAtEpochMs(), is(greaterThan(0L)));
        assertThat("Response should not be null", response, is(notNullValue()));
    }

    @Test
    void testMockInitiateUpload_WithDefaults() {
        // Setup mock with defaults
        repositoryServiceMock.mockInitiateUpload();

        // Call
        var response = uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("test-file.txt")
                .build()
        );

        // Verify with Hamcrest matchers
        assertThat("Node ID should not be null", response.getNodeId(), is(notNullValue()));
        assertThat("Node ID should not be empty", response.getNodeId(), is(not(emptyString())));
        assertThat("Upload ID should not be null", response.getUploadId(), is(notNullValue()));
        assertThat("Upload ID should not be empty", response.getUploadId(), is(not(emptyString())));
        assertThat("State should be PENDING", response.getState(), is(UploadState.UPLOAD_STATE_PENDING));
        assertThat("Response should not be null", response, is(notNullValue()));
    }

    @Test
    void testMockInitiateUpload_InvalidArgument() {
        // Setup mock for invalid argument
        repositoryServiceMock.mockInitiateUploadInvalidArgument("Missing required field: drive");

        // Call - should throw INVALID_ARGUMENT
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setName("test-file.txt")
                .build()
        ));

        assertEquals(io.grpc.Status.Code.INVALID_ARGUMENT, exception.getStatus().getCode());
        String description = exception.getStatus().getDescription();
        assertNotNull(description);
        assertTrue(description.contains("Missing required field: drive"));
    }

    @Test
    void testMockInitiateUpload_NotFound() {
        // Setup mock for not found
        repositoryServiceMock.mockInitiateUploadNotFound("Drive not found: missing-drive");

        // Call - should throw NOT_FOUND
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("missing-drive")
                .setName("test-file.txt")
                .build()
        ));

        assertEquals(io.grpc.Status.Code.NOT_FOUND, exception.getStatus().getCode());
        String description = exception.getStatus().getDescription();
        assertNotNull(description);
        assertTrue(description.contains("Drive not found"));
    }

    @Test
    void testMockInitiateUpload_ResourceExhausted() {
        // Setup mock for resource exhausted
        repositoryServiceMock.mockInitiateUploadResourceExhausted("Quota exceeded for drive");

        // Call - should throw RESOURCE_EXHAUSTED
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("test-file.txt")
                .build()
        ));

        assertEquals(io.grpc.Status.Code.RESOURCE_EXHAUSTED, exception.getStatus().getCode());
    }

    @Test
    void testMockInitiateUpload_InternalError() {
        // Setup mock for internal error
        repositoryServiceMock.mockInitiateUploadInternalError("Internal server error");

        // Call - should throw INTERNAL
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("test-file.txt")
                .build()
        ));

        assertEquals(io.grpc.Status.Code.INTERNAL, exception.getStatus().getCode());
    }

    @Test
    void testMockUploadChunk_Success() {
        // Setup mock
        repositoryServiceMock.mockUploadChunk("test-node-123", 1);

        // Call
        var response = uploadService.uploadChunk(
            UploadChunkRequest.newBuilder()
                .setNodeId("test-node-123")
                .setUploadId("upload-456")
                .setChunkNumber(1)
                .setData(com.google.protobuf.ByteString.copyFromUtf8("chunk data"))
                .build()
        );

        // Verify with Hamcrest matchers
        assertThat("Node ID should match", response.getNodeId(), is(equalTo("test-node-123")));
        assertThat("Chunk number should match", response.getChunkNumber(), is(equalTo(1L)));
        assertThat("State should be UPLOADING", response.getState(), is(UploadState.UPLOAD_STATE_UPLOADING));
        assertThat("File should not be complete", response.getIsFileComplete(), is(false));
        assertThat("Response should not be null", response, is(notNullValue()));
    }

    @Test
    void testMockUploadChunk_Failed() {
        // Setup mock for chunk upload failure
        repositoryServiceMock.mockUploadChunkFailed("test-node-123", "Chunk processing failed");

        // Call - should throw INTERNAL
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> uploadService.uploadChunk(
            UploadChunkRequest.newBuilder()
                .setNodeId("test-node-123")
                .setUploadId("upload-456")
                .setChunkNumber(1)
                .setData(com.google.protobuf.ByteString.copyFromUtf8("chunk data"))
                .build()
        ));

        assertEquals(io.grpc.Status.Code.INTERNAL, exception.getStatus().getCode());
        String description = exception.getStatus().getDescription();
        assertNotNull(description);
        assertTrue(description.contains("Chunk processing failed"));
    }

    @Test
    void testMockUploadChunk_NotFound() {
        // Setup mock for upload not found
        repositoryServiceMock.mockUploadChunkNotFound("missing-node", "Upload not found");

        // Call - should throw NOT_FOUND
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> uploadService.uploadChunk(
            UploadChunkRequest.newBuilder()
                .setNodeId("missing-node")
                .setUploadId("upload-456")
                .setChunkNumber(1)
                .setData(com.google.protobuf.ByteString.copyFromUtf8("chunk data"))
                .build()
        ));

        assertEquals(io.grpc.Status.Code.NOT_FOUND, exception.getStatus().getCode());
    }

    @Test
    void testMockGetUploadStatus_Success() {
        // Setup mock
        repositoryServiceMock.mockGetUploadStatus("test-node-123", UploadState.UPLOAD_STATE_UPLOADING);

        // Call
        var response = uploadService.getUploadStatus(
            GetUploadStatusRequest.newBuilder()
                .setNodeId("test-node-123")
                .build()
        );

        // Verify with Hamcrest matchers
        assertThat("Node ID should match", response.getNodeId(), is(equalTo("test-node-123")));
        assertThat("State should match", response.getState(), is(UploadState.UPLOAD_STATE_UPLOADING));
        assertThat("Updated timestamp should be positive", response.getUpdatedAtEpochMs(), is(greaterThan(0L)));
        assertThat("Response should not be null", response, is(notNullValue()));
    }

    @Test
    void testMockGetUploadStatus_NotFound() {
        // Setup mock for not found
        repositoryServiceMock.mockGetUploadStatusNotFound("missing-node");

        // Call - should throw NOT_FOUND
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> uploadService.getUploadStatus(
            GetUploadStatusRequest.newBuilder()
                .setNodeId("missing-node")
                .build()
        ));

        assertEquals(io.grpc.Status.Code.NOT_FOUND, exception.getStatus().getCode());
        String description = exception.getStatus().getDescription();
        assertNotNull(description);
        assertTrue(description.contains("Upload not found"));
    }

    @Test
    void testMockCancelUpload_Success() {
        // Setup mock
        repositoryServiceMock.mockCancelUpload("test-node-123");

        // Call
        var response = uploadService.cancelUpload(
            CancelUploadRequest.newBuilder()
                .setNodeId("test-node-123")
                .build()
        );

        // Verify with Hamcrest matchers
        assertThat("Node ID should match", response.getNodeId(), is(equalTo("test-node-123")));
        assertThat("Cancel should be successful", response.getSuccess(), is(true));
        assertThat("Message should match", response.getMessage(), is(equalTo("Upload cancelled successfully")));
        assertThat("Response should not be null", response, is(notNullValue()));
    }

    @Test
    void testMockCancelUpload_Failed() {
        // Setup mock for cancel failure
        repositoryServiceMock.mockCancelUploadFailed("test-node-123", "Upload already completed");

        // Call - should throw FAILED_PRECONDITION
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> uploadService.cancelUpload(
            CancelUploadRequest.newBuilder()
                .setNodeId("test-node-123")
                .build()
        ));

        assertEquals(io.grpc.Status.Code.FAILED_PRECONDITION, exception.getStatus().getCode());
        String description = exception.getStatus().getDescription();
        assertNotNull(description);
        assertTrue(description.contains("Upload already completed"));
    }

    @Test
    void testMockCancelUpload_NotFound() {
        // Setup mock for not found
        repositoryServiceMock.mockCancelUploadNotFound("missing-node");

        // Call - should throw NOT_FOUND
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> uploadService.cancelUpload(
            CancelUploadRequest.newBuilder()
                .setNodeId("missing-node")
                .build()
        ));

        assertEquals(io.grpc.Status.Code.NOT_FOUND, exception.getStatus().getCode());
        String description = exception.getStatus().getDescription();
        assertNotNull(description);
        assertTrue(description.contains("Upload not found"));
    }

    @Test
    void testSetupDefaults() {
        // Setup defaults
        repositoryServiceMock.setupDefaults();

        // Call
        var response = uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("test-file.txt")
                .build()
        );

        // Verify defaults are set with Hamcrest matchers
        assertThat("Node ID should not be null", response.getNodeId(), is(notNullValue()));
        assertThat("Node ID should not be empty", response.getNodeId(), is(not(emptyString())));
        assertThat("Upload ID should not be null", response.getUploadId(), is(notNullValue()));
        assertThat("Upload ID should not be empty", response.getUploadId(), is(not(emptyString())));
        assertThat("State should be PENDING", response.getState(), is(UploadState.UPLOAD_STATE_PENDING));
        assertThat("Response should not be null", response, is(notNullValue()));
    }
}

