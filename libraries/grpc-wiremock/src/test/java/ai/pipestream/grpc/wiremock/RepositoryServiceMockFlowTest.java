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
 * Tests for RepositoryServiceMock.setupChunkedUploadFlow() method.
 * <p>
 * These tests demonstrate how to use the convenience method to setup
 * complete chunked upload flows in a single call.
 */
public class RepositoryServiceMockFlowTest {

    private WireMockServer wireMockServer;
    private ManagedChannel channel;
    private NodeUploadServiceGrpc.NodeUploadServiceBlockingStub uploadService;
    private RepositoryServiceMock repositoryMock;
    private WireMockGrpcVerifier verifier;

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

        // Create repository mock
        repositoryMock = new RepositoryServiceMock(wireMockServer.port());

        // Create verifier
        verifier = new WireMockGrpcVerifier(wireMockServer);

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
    void testSetupChunkedUploadFlow_CompleteFlow() {
        // Setup complete flow with 3 chunks
        String nodeId = "node-123";
        String uploadId = "upload-456";
        int totalChunks = 3;

        repositoryMock.setupChunkedUploadFlow(nodeId, uploadId, totalChunks);

        // Step 1: Initiate upload
        InitiateUploadResponse initiateResponse = uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("test.txt")
                .build()
        );

        assertThat("Node ID should match", initiateResponse.getNodeId(), is(equalTo(nodeId)));
        assertThat("Upload ID should match", initiateResponse.getUploadId(), is(equalTo(uploadId)));
        assertThat("State should be PENDING", 
            initiateResponse.getState(), is(UploadState.UPLOAD_STATE_PENDING));

        // Step 2: Upload all chunks
        for (int i = 1; i <= totalChunks; i++) {
            boolean isLast = (i == totalChunks);
            UploadChunkResponse chunkResponse = uploadService.uploadChunk(
                UploadChunkRequest.newBuilder()
                    .setNodeId(nodeId)
                    .setUploadId(uploadId)
                    .setChunkNumber(i)
                    .setData(com.google.protobuf.ByteString.copyFromUtf8("chunk-" + i))
                    .setIsLast(isLast)
                    .build()
            );
            assertThat("Chunk number should match", 
                chunkResponse.getChunkNumber(), is(equalTo((long) i)));
            // mockUploadChunkDynamic now correctly returns isFileComplete based on isLast field
            assertThat("isFileComplete should match isLast", 
                chunkResponse.getIsFileComplete(), is(equalTo(isLast)));
        }

        // Step 3: Get upload status (should be COMPLETED)
        GetUploadStatusResponse statusResponse = uploadService.getUploadStatus(
            GetUploadStatusRequest.newBuilder()
                .setNodeId(nodeId)
                .build()
        );

        assertThat("State should be COMPLETED", 
            statusResponse.getState(), is(UploadState.UPLOAD_STATE_COMPLETED));

        // Verify all calls were made
        verifier.verifyMethodCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "InitiateUpload"
        );
        verifier.verifyMethodCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "UploadChunk",
            totalChunks
        );
        verifier.verifyMethodCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "GetUploadStatus"
        );
    }

    @Test
    void testSetupChunkedUploadFlow_SingleChunk() {
        // Test with single chunk
        String nodeId = "node-single";
        String uploadId = "upload-single";

        repositoryMock.setupChunkedUploadFlow(nodeId, uploadId, 1);

        // Execute flow
        uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("single.txt")
                .build()
        );

        UploadChunkResponse chunkResponse = uploadService.uploadChunk(
            UploadChunkRequest.newBuilder()
                .setNodeId(nodeId)
                .setUploadId(uploadId)
                .setChunkNumber(1)
                .setData(com.google.protobuf.ByteString.copyFromUtf8("single-chunk"))
                .setIsLast(true)  // Single chunk is also the last chunk
                .build()
        );
        
        // Verify isFileComplete is true for the last (and only) chunk
        assertThat("isFileComplete should be true for last chunk", 
            chunkResponse.getIsFileComplete(), is(true));

        GetUploadStatusResponse statusResponse = uploadService.getUploadStatus(
            GetUploadStatusRequest.newBuilder()
                .setNodeId(nodeId)
                .build()
        );

        assertThat("State should be COMPLETED", 
            statusResponse.getState(), is(UploadState.UPLOAD_STATE_COMPLETED));

        // Verify single chunk was uploaded
        verifier.verifyMethodCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "UploadChunk",
            1
        );
    }

    @Test
    void testSetupChunkedUploadFlow_ManyChunks() {
        // Test with many chunks
        String nodeId = "node-many";
        String uploadId = "upload-many";
        int totalChunks = 10;

        repositoryMock.setupChunkedUploadFlow(nodeId, uploadId, totalChunks);

        // Execute flow
        uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("many.txt")
                .build()
        );

        for (int i = 1; i <= totalChunks; i++) {
            boolean isLast = (i == totalChunks);
            UploadChunkResponse chunkResponse = uploadService.uploadChunk(
                UploadChunkRequest.newBuilder()
                    .setNodeId(nodeId)
                    .setUploadId(uploadId)
                    .setChunkNumber(i)
                    .setData(com.google.protobuf.ByteString.copyFromUtf8("chunk-" + i))
                    .setIsLast(isLast)
                    .build()
            );
            // Verify isFileComplete matches isLast
            assertThat("isFileComplete should match isLast for chunk " + i, 
                chunkResponse.getIsFileComplete(), is(equalTo(isLast)));
        }

        GetUploadStatusResponse statusResponse = uploadService.getUploadStatus(
            GetUploadStatusRequest.newBuilder()
                .setNodeId(nodeId)
                .build()
        );

        assertThat("State should be COMPLETED", 
            statusResponse.getState(), is(UploadState.UPLOAD_STATE_COMPLETED));

        // Verify all chunks were uploaded
        verifier.verifyMethodCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "UploadChunk",
            totalChunks
        );
    }

    @Test
    void testSetupChunkedUploadFlow_CanBeChained() {
        // Verify method chaining works
        String nodeId = "node-chain";
        String uploadId = "upload-chain";

        RepositoryServiceMock chained = repositoryMock
            .setupChunkedUploadFlow(nodeId, uploadId, 2)
            .mockCancelUpload(nodeId); // Can chain additional mocks

        assertThat("Should return same instance", chained, is(sameInstance(repositoryMock)));

        // Verify the flow works
        uploadService.initiateUpload(
            InitiateUploadRequest.newBuilder()
                .setDrive("test-drive")
                .setName("chain.txt")
                .build()
        );

        assertThat("Initiate should work", 
            uploadService.initiateUpload(
                InitiateUploadRequest.newBuilder()
                    .setDrive("test-drive")
                    .setName("chain2.txt")
                    .build()
            ).getNodeId(), is(equalTo(nodeId)));
    }
}

