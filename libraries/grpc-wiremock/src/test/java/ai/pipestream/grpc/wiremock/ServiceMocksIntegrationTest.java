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

/**
 * Integration tests demonstrating how to use ServiceMocks with the new
 * verifier and chunked upload flow helper.
 * <p>
 * These tests show the complete integration of all components working together.
 */
public class ServiceMocksIntegrationTest {

    private WireMockServer wireMockServer;
    private ManagedChannel channel;
    private NodeUploadServiceGrpc.NodeUploadServiceBlockingStub uploadService;
    private ServiceMocks serviceMocks;

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

        // Create ServiceMocks
        serviceMocks = new ServiceMocks(wireMockServer);

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
    void testServiceMocks_VerifierAccess() {
        // Setup mock
        serviceMocks.repository().mockInitiateUpload("node-123", "upload-456");

        // Make call
        uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("test.txt")
                .build()
        );

        // Use verifier from ServiceMocks
        WireMockGrpcVerifier verifier = serviceMocks.verifier();
        verifier.verifyMethodCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "InitiateUpload"
        );

        int count = verifier.getRequestCount(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "InitiateUpload"
        );
        assertThat("Count should be 1", count, is(1));
    }

    @Test
    void testServiceMocks_ChunkedUploadFlowAccess() {
        // Use ChunkedUploadFlowHelper from ServiceMocks
        ChunkedUploadFlowHelper flowHelper = serviceMocks.chunkedUploadFlow();

        // Setup complete flow
        ChunkedUploadFlowHelper.UploadIds ids = flowHelper.setupSuccessfulFlow(3);

        // Execute flow
        uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("test.txt")
                .build()
        );

        for (int i = 1; i <= 3; i++) {
            uploadService.uploadChunk(
                UploadChunkRequest.newBuilder()
                    .setNodeId(ids.nodeId)
                    .setUploadId(ids.uploadId)
                    .setChunkNumber(i)
                    .setData(com.google.protobuf.ByteString.copyFromUtf8("chunk-" + i))
                    .build()
            );
        }

        uploadService.getUploadStatus(
            GetUploadStatusRequest.newBuilder()
                .setNodeId(ids.nodeId)
                .build()
        );

        // Verify using flow helper
        flowHelper.verifyInitiateUploadCalled()
            .verifyUploadChunkCalled(3)
            .verifyGetUploadStatusCalled();
    }

    @Test
    void testServiceMocks_CompleteIntegration() {
        // Setup defaults for all services
        serviceMocks.setupDefaults();

        // Setup specific chunked upload flow
        serviceMocks.repository().setupChunkedUploadFlow("node-123", "upload-456", 2);

        // Execute chunked upload flow
        InitiateUploadResponse initiateResponse = uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("test.txt")
                .build()
        );

        assertThat("Should get node ID", initiateResponse.getNodeId(), is(equalTo("node-123")));

        // Upload chunks
        for (int i = 1; i <= 2; i++) {
            uploadService.uploadChunk(
                UploadChunkRequest.newBuilder()
                    .setNodeId("node-123")
                    .setUploadId("upload-456")
                    .setChunkNumber(i)
                    .setData(com.google.protobuf.ByteString.copyFromUtf8("chunk-" + i))
                    .build()
            );
        }

        // Get status
        GetUploadStatusResponse statusResponse =         uploadService.getUploadStatus(
            GetUploadStatusRequest.newBuilder()
                .setNodeId("node-123")
                .build()
        );

        assertThat("State should be COMPLETED", 
            statusResponse.getState(), is(UploadState.UPLOAD_STATE_COMPLETED));

        // Verify using ServiceMocks verifier
        WireMockGrpcVerifier verifier = serviceMocks.verifier();
        verifier.verifyMethodCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "InitiateUpload"
        );
        verifier.verifyMethodCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "UploadChunk",
            2
        );
        verifier.verifyMethodCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "GetUploadStatus"
        );
    }

    @Test
    void testServiceMocks_MultipleFlows() {
        // Setup multiple flows using different helpers
        ChunkedUploadFlowHelper flow1 = serviceMocks.chunkedUploadFlow();
        ChunkedUploadFlowHelper flow2 = new ChunkedUploadFlowHelper(wireMockServer);

        // Setup first flow
        ChunkedUploadFlowHelper.UploadIds ids1 = flow1.setupSuccessfulFlow(2);

        // Setup second flow
        flow2.setupSuccessfulFlow("node-789", "upload-012", 3);
        ChunkedUploadFlowHelper.UploadIds ids2 = new ChunkedUploadFlowHelper.UploadIds("node-789", "upload-012");

        // Execute first flow
        uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("flow1.txt")
                .build()
        );

        for (int i = 1; i <= 2; i++) {
            uploadService.uploadChunk(
                UploadChunkRequest.newBuilder()
                    .setNodeId(ids1.nodeId)
                    .setUploadId(ids1.uploadId)
                    .setChunkNumber(i)
                    .setData(com.google.protobuf.ByteString.copyFromUtf8("flow1-chunk-" + i))
                    .build()
            );
        }

        // Execute second flow
        uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("flow2.txt")
                .build()
        );

        for (int i = 1; i <= 3; i++) {
            uploadService.uploadChunk(
                UploadChunkRequest.newBuilder()
                    .setNodeId(ids2.nodeId)
                    .setUploadId(ids2.uploadId)
                    .setChunkNumber(i)
                    .setData(com.google.protobuf.ByteString.copyFromUtf8("flow2-chunk-" + i))
                    .build()
            );
        }

        // Verify using ServiceMocks verifier (counts all calls)
        WireMockGrpcVerifier verifier = serviceMocks.verifier();
        verifier.verifyMethodCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "InitiateUpload",
            2  // Two flows = two initiate calls
        );
        verifier.verifyMethodCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "UploadChunk",
            5  // 2 + 3 = 5 total chunks
        );
    }

    @Test
    void testServiceMocks_ResetAndReuse() {
        // Setup flow
        serviceMocks.repository().setupChunkedUploadFlow("node-123", "upload-456", 1);

        // Execute flow
        uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("test.txt")
                .build()
        );

        // Verify call was made
        WireMockGrpcVerifier verifier = serviceMocks.verifier();
        int countBefore = verifier.getRequestCount(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "InitiateUpload"
        );
        assertThat("Count should be 1 before reset", countBefore, is(1));

        // Reset ServiceMocks
        serviceMocks.reset();

        // Reset verifier
        verifier.resetRequests();

        // Verify count is zero
        int countAfter = verifier.getRequestCount(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "InitiateUpload"
        );
        assertThat("Count should be 0 after reset", countAfter, is(0));

        // Can setup new flow after reset
        serviceMocks.repository().setupChunkedUploadFlow("node-new", "upload-new", 1);
        uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("new.txt")
                .build()
        );

        verifier.verifyMethodCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "InitiateUpload"
        );
    }
}

