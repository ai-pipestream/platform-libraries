package ai.pipestream.grpc.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import ai.pipestream.repository.filesystem.upload.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

        // Verify
        assertEquals("test-node-123", response.getNodeId());
        assertEquals("upload-456", response.getUploadId());
        assertEquals(UploadState.UPLOAD_STATE_PENDING, response.getState());
        assertFalse(response.getIsUpdate());
        assertTrue(response.getCreatedAtEpochMs() > 0);
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

        // Verify
        assertNotNull(response.getNodeId());
        assertNotNull(response.getUploadId());
        assertEquals(UploadState.UPLOAD_STATE_PENDING, response.getState());
    }

    @Test
    void testMockInitiateUpload_InvalidArgument() {
        // Setup mock for invalid argument
        repositoryServiceMock.mockInitiateUploadInvalidArgument("Missing required field: drive");

        // Call - should throw INVALID_ARGUMENT
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            uploadService.initiateUpload(
                InitiateUploadRequest.newBuilder()
                    .setName("test-file.txt")
                    .build()
            );
        });

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
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            uploadService.initiateUpload(
                InitiateUploadRequest.newBuilder()
                    .setDrive("missing-drive")
                    .setName("test-file.txt")
                    .build()
            );
        });

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
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            uploadService.initiateUpload(
                InitiateUploadRequest.newBuilder()
                    .setDrive("test-drive")
                    .setName("test-file.txt")
                    .build()
            );
        });

        assertEquals(io.grpc.Status.Code.RESOURCE_EXHAUSTED, exception.getStatus().getCode());
    }

    @Test
    void testMockInitiateUpload_InternalError() {
        // Setup mock for internal error
        repositoryServiceMock.mockInitiateUploadInternalError("Internal server error");

        // Call - should throw INTERNAL
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            uploadService.initiateUpload(
                InitiateUploadRequest.newBuilder()
                    .setDrive("test-drive")
                    .setName("test-file.txt")
                    .build()
            );
        });

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

        // Verify
        assertEquals("test-node-123", response.getNodeId());
        assertEquals(1, response.getChunkNumber());
        assertEquals(UploadState.UPLOAD_STATE_UPLOADING, response.getState());
        assertFalse(response.getIsFileComplete());
    }

    @Test
    void testMockUploadChunk_Failed() {
        // Setup mock for chunk upload failure
        repositoryServiceMock.mockUploadChunkFailed("test-node-123", "Chunk processing failed");

        // Call - should throw INTERNAL
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            uploadService.uploadChunk(
                UploadChunkRequest.newBuilder()
                    .setNodeId("test-node-123")
                    .setUploadId("upload-456")
                    .setChunkNumber(1)
                    .setData(com.google.protobuf.ByteString.copyFromUtf8("chunk data"))
                    .build()
            );
        });

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
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            uploadService.uploadChunk(
                UploadChunkRequest.newBuilder()
                    .setNodeId("missing-node")
                    .setUploadId("upload-456")
                    .setChunkNumber(1)
                    .setData(com.google.protobuf.ByteString.copyFromUtf8("chunk data"))
                    .build()
            );
        });

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

        // Verify
        assertEquals("test-node-123", response.getNodeId());
        assertEquals(UploadState.UPLOAD_STATE_UPLOADING, response.getState());
        assertTrue(response.getUpdatedAtEpochMs() > 0);
    }

    @Test
    void testMockGetUploadStatus_NotFound() {
        // Setup mock for not found
        repositoryServiceMock.mockGetUploadStatusNotFound("missing-node");

        // Call - should throw NOT_FOUND
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            uploadService.getUploadStatus(
                GetUploadStatusRequest.newBuilder()
                    .setNodeId("missing-node")
                    .build()
            );
        });

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

        // Verify
        assertEquals("test-node-123", response.getNodeId());
        assertTrue(response.getSuccess());
        assertEquals("Upload cancelled successfully", response.getMessage());
    }

    @Test
    void testMockCancelUpload_Failed() {
        // Setup mock for cancel failure
        repositoryServiceMock.mockCancelUploadFailed("test-node-123", "Upload already completed");

        // Call - should throw FAILED_PRECONDITION
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            uploadService.cancelUpload(
                CancelUploadRequest.newBuilder()
                    .setNodeId("test-node-123")
                    .build()
            );
        });

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
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            uploadService.cancelUpload(
                CancelUploadRequest.newBuilder()
                    .setNodeId("missing-node")
                    .build()
            );
        });

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

        // Verify defaults are set
        assertNotNull(response.getNodeId());
        assertNotNull(response.getUploadId());
        assertEquals(UploadState.UPLOAD_STATE_PENDING, response.getState());
    }
}

