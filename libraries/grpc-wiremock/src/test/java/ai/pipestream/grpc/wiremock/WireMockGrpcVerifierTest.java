package ai.pipestream.grpc.wiremock;

import ai.pipestream.repository.filesystem.upload.*;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Comprehensive tests for WireMockGrpcVerifier.
 * <p>
 * These tests demonstrate how to verify gRPC calls made to WireMock,
 * including call counts, request matching, and verification failures.
 */
public class WireMockGrpcVerifierTest {

    private WireMockServer wireMockServer;
    private ManagedChannel channel;
    private NodeUploadServiceGrpc.NodeUploadServiceBlockingStub uploadService;
    private WireMockGrpcVerifier verifier;
    private RepositoryServiceMock repositoryMock;

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

        // Create verifier
        verifier = new WireMockGrpcVerifier(wireMockServer);

        // Create repository mock
        repositoryMock = new RepositoryServiceMock(wireMockServer.port());

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
    void testVerifyMethodCalled_Success() {
        // Setup mock
        repositoryMock.mockInitiateUpload("node-123", "upload-456");

        // Make call
        uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("test.txt")
                .build()
        );

        // Verify call was made
        verifier.verifyMethodCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "InitiateUpload"
        );
    }

    @Test
    void testVerifyMethodCalled_Failure_NoCalls() {
        // Don't make any calls

        // Verify should fail
        AssertionError error = assertThrows(AssertionError.class, () -> {
            verifier.verifyMethodCalled(
                "ai.pipestream.repository.filesystem.upload.NodeUploadService",
                "InitiateUpload"
            );
        });

        assertThat("Error message should indicate no calls", 
            error.getMessage(), containsString("Expected at least 1 call"));
    }

    @Test
    void testVerifyMethodCalled_WithExactCount_Success() {
        // Setup mock
        repositoryMock.mockUploadChunk("node-123", 1);
        repositoryMock.mockUploadChunk("node-123", 2);
        repositoryMock.mockUploadChunk("node-123", 3);

        // Make 3 calls
        for (int i = 1; i <= 3; i++) {
            uploadService.uploadChunk(
                UploadChunkRequest.newBuilder()
                    .setNodeId("node-123")
                    .setUploadId("upload-456")
                    .setChunkNumber(i)
                    .setData(com.google.protobuf.ByteString.copyFromUtf8("chunk-" + i))
                    .build()
            );
        }

        // Verify exactly 3 calls
        verifier.verifyMethodCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "UploadChunk",
            3
        );
    }

    @Test
    void testVerifyMethodCalled_WithExactCount_Failure_WrongCount() {
        // Setup mock
        repositoryMock.mockUploadChunk("node-123", 1);

        // Make only 1 call
        uploadService.uploadChunk(
            UploadChunkRequest.newBuilder()
                .setNodeId("node-123")
                .setUploadId("upload-456")
                .setChunkNumber(1)
                .setData(com.google.protobuf.ByteString.copyFromUtf8("chunk-1"))
                .build()
        );

        // Verify should fail when expecting 3 calls
        AssertionError error = assertThrows(AssertionError.class, () -> {
            verifier.verifyMethodCalled(
                "ai.pipestream.repository.filesystem.upload.NodeUploadService",
                "UploadChunk",
                3
            );
        });

        assertThat("Error message should indicate wrong count", 
            error.getMessage(), containsString("Expected 3 calls"));
        assertThat("Error message should show actual count", 
            error.getMessage(), containsString("but got 1"));
    }

    @Test
    void testVerifyMethodCalledWith_Success() {
        // Setup mock with request matching
        InitiateUploadRequest expectedRequest = InitiateUploadRequest.newBuilder()
            .setDrive("test-drive")
            .setName("test.txt")
            .setConnectorId("connector-123")
            .build();

        repositoryMock.mockInitiateUpload("node-123", "upload-456", expectedRequest);

        // Make call with matching request
        uploadService.initiateUpload(expectedRequest);

        // Verify call was made with matching request
        verifier.verifyMethodCalledWith(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "InitiateUpload",
            expectedRequest
        );
    }

    @Test
    void testVerifyMethodCalledWith_Failure_NonMatchingRequest() {
        // Setup mock with specific request matching
        InitiateUploadRequest expectedRequest = InitiateUploadRequest.newBuilder()
            .setDrive("test-drive")
            .setName("test.txt")
            .setConnectorId("connector-123")
            .build();

        repositoryMock.mockInitiateUpload("node-123", "upload-456", expectedRequest);

        // Also setup a default mock so the different request doesn't fail
        // This allows us to test verification of non-matching requests
        repositoryMock.mockInitiateUpload("node-default", "upload-default");

        // Make call with different request (will match the default mock)
        InitiateUploadRequest actualRequest = InitiateUploadRequest.newBuilder()
            .setDrive("different-drive")
            .setName("different.txt")
            .build();

        uploadService.initiateUpload(actualRequest);

        // Verify should fail - request doesn't match the expected one
        AssertionError error = assertThrows(AssertionError.class, () -> {
            verifier.verifyMethodCalledWith(
                "ai.pipestream.repository.filesystem.upload.NodeUploadService",
                "InitiateUpload",
                expectedRequest
            );
        });

        assertThat("Error message should indicate no matching calls", 
            error.getMessage(), containsString("Expected at least 1 call"));
    }

    @Test
    void testVerifyMethodNeverCalled_Success() {
        // Don't make any calls

        // Verify method was never called
        verifier.verifyMethodNeverCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "CancelUpload"
        );
    }

    @Test
    void testVerifyMethodNeverCalled_Failure_CallWasMade() {
        // Setup mock
        repositoryMock.mockCancelUpload("node-123");

        // Make call
        uploadService.cancelUpload(
            CancelUploadRequest.newBuilder()
                .setNodeId("node-123")
                .build()
        );

        // Verify should fail - call was made
        AssertionError error = assertThrows(AssertionError.class, () -> {
            verifier.verifyMethodNeverCalled(
                "ai.pipestream.repository.filesystem.upload.NodeUploadService",
                "CancelUpload"
            );
        });

        assertThat("Error message should indicate call was made", 
            error.getMessage(), containsString("Expected 0 calls"));
        assertThat("Error message should show actual count", 
            error.getMessage(), containsString("but got 1"));
    }

    @Test
    void testGetRequestCount_ZeroCalls() {
        // Don't make any calls

        int count = verifier.getRequestCount(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "InitiateUpload"
        );

        assertThat("Count should be zero", count, is(0));
    }

    @Test
    void testGetRequestCount_MultipleCalls() {
        // Setup mock
        repositoryMock.mockUploadChunk("node-123", 1);

        // Make 5 calls
        for (int i = 1; i <= 5; i++) {
            uploadService.uploadChunk(
                UploadChunkRequest.newBuilder()
                    .setNodeId("node-123")
                    .setUploadId("upload-456")
                    .setChunkNumber(i)
                    .setData(com.google.protobuf.ByteString.copyFromUtf8("chunk-" + i))
                    .build()
            );
        }

        int count = verifier.getRequestCount(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "UploadChunk"
        );

        assertThat("Count should be 5", count, is(5));
    }

    @Test
    void testResetRequests_ClearsJournal() {
        // Setup mock
        repositoryMock.mockInitiateUpload("node-123", "upload-456");

        // Make call
        uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("test.txt")
                .build()
        );

        // Verify call was made
        int countBefore = verifier.getRequestCount(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "InitiateUpload"
        );
        assertThat("Count should be 1 before reset", countBefore, is(1));

        // Reset requests
        verifier.resetRequests();

        // Verify count is now zero
        int countAfter = verifier.getRequestCount(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "InitiateUpload"
        );
        assertThat("Count should be 0 after reset", countAfter, is(0));
    }

    @Test
    void testVerifyMultipleMethods() {
        // Setup mocks for multiple methods
        repositoryMock.mockInitiateUpload("node-123", "upload-456");
        repositoryMock.mockUploadChunk("node-123", 1);
        repositoryMock.mockGetUploadStatus("node-123", UploadState.UPLOAD_STATE_PENDING);

        // Make calls to different methods
        uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("test.txt")
                .build()
        );

        uploadService.uploadChunk(
            UploadChunkRequest.newBuilder()
                .setNodeId("node-123")
                .setUploadId("upload-456")
                .setChunkNumber(1)
                .setData(com.google.protobuf.ByteString.copyFromUtf8("chunk-1"))
                .build()
        );

        uploadService.getUploadStatus(
            GetUploadStatusRequest.newBuilder()
                .setNodeId("node-123")
                .build()
        );

        // Verify all methods were called
        verifier.verifyMethodCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "InitiateUpload"
        );
        verifier.verifyMethodCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "UploadChunk"
        );
        verifier.verifyMethodCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "GetUploadStatus"
        );

        // Verify counts
        assertThat("InitiateUpload count", 
            verifier.getRequestCount(
                "ai.pipestream.repository.filesystem.upload.NodeUploadService",
                "InitiateUpload"
            ), is(1));
        assertThat("UploadChunk count", 
            verifier.getRequestCount(
                "ai.pipestream.repository.filesystem.upload.NodeUploadService",
                "UploadChunk"
            ), is(1));
        assertThat("GetUploadStatus count", 
            verifier.getRequestCount(
                "ai.pipestream.repository.filesystem.upload.NodeUploadService",
                "GetUploadStatus"
            ), is(1));
    }
}

