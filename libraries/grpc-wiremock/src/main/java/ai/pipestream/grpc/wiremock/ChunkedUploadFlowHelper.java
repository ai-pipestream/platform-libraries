package ai.pipestream.grpc.wiremock;

import ai.pipestream.repository.filesystem.upload.*;
import com.github.tomakehurst.wiremock.WireMockServer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Helper class for managing chunked upload flow state and mocks.
 * <p>
 * This class helps track nodeId/uploadId mappings and provides convenience methods
 * to set up complete chunked upload flows for integration testing.
 */
public class ChunkedUploadFlowHelper {
    
    private final RepositoryServiceMock repositoryMock;
    private final WireMockGrpcVerifier verifier;
    private final Map<String, UploadState> uploadStates = new HashMap<>();
    
    /**
     * Creates a helper for the given WireMock server.
     *
     * @param wireMockServer The WireMock server instance
     */
    public ChunkedUploadFlowHelper(WireMockServer wireMockServer) {
        this.repositoryMock = new RepositoryServiceMock(wireMockServer.port());
        this.verifier = new WireMockGrpcVerifier(wireMockServer);
    }
    
    /**
     * Setup a complete successful chunked upload flow.
     * <p>
     * This method sets up mocks for:
     * - InitiateUpload (returns the provided nodeId and uploadId)
     * - UploadChunk (for chunks 1 through totalChunks, with request matching)
     * - GetUploadStatus (returns COMPLETED state)
     *
     * @param nodeId The node ID to use for the upload
     * @param uploadId The upload ID to use for the upload
     * @param totalChunks The total number of chunks that will be uploaded
     * @return this instance for method chaining
     */
    public ChunkedUploadFlowHelper setupSuccessfulFlow(String nodeId, String uploadId, int totalChunks) {
        // Setup InitiateUpload
        repositoryMock.mockInitiateUpload(nodeId, uploadId);
        
        // Use dynamic JSON template approach to avoid WireMock bug #1230 with multiple stubs.
        // This creates a single stub that handles all chunks dynamically.
        repositoryMock.mockUploadChunkDynamic(nodeId, totalChunks);
        
        // Setup GetUploadStatus to return COMPLETED
        repositoryMock.mockGetUploadStatus(nodeId, UploadState.UPLOAD_STATE_COMPLETED);
        
        // Track state
        uploadStates.put(uploadId, UploadState.UPLOAD_STATE_COMPLETED);
        
        return this;
    }
    
    /**
     * Setup a complete successful chunked upload flow with auto-generated IDs.
     *
     * @param totalChunks The total number of chunks that will be uploaded
     * @return UploadIds containing the generated nodeId and uploadId
     */
    public UploadIds setupSuccessfulFlow(int totalChunks) {
        String nodeId = "node-" + UUID.randomUUID().toString().substring(0, 8);
        String uploadId = "upload-" + UUID.randomUUID().toString().substring(0, 8);
        setupSuccessfulFlow(nodeId, uploadId, totalChunks);
        return new UploadIds(nodeId, uploadId);
    }
    
    /**
     * Setup InitiateUpload only (for step-by-step flow control).
     *
     * @param nodeId The node ID to return
     * @param uploadId The upload ID to return
     * @return this instance for method chaining
     */
    public ChunkedUploadFlowHelper setupInitiateUpload(String nodeId, String uploadId) {
        repositoryMock.mockInitiateUpload(nodeId, uploadId);
        uploadStates.put(uploadId, UploadState.UPLOAD_STATE_PENDING);
        return this;
    }
    
    /**
     * Setup UploadChunk for a specific chunk number.
     *
     * @param nodeId The node ID
     * @param chunkNumber The chunk number
     * @return this instance for method chaining
     */
    public ChunkedUploadFlowHelper setupUploadChunk(String nodeId, int chunkNumber) {
        repositoryMock.mockUploadChunk(nodeId, chunkNumber);
        return this;
    }
    
    /**
     * Setup GetUploadStatus to return a specific state.
     *
     * @param nodeId The node ID
     * @param state The upload state to return
     * @return this instance for method chaining
     */
    public ChunkedUploadFlowHelper setupGetUploadStatus(String nodeId, UploadState state) {
        repositoryMock.mockGetUploadStatus(nodeId, state);
        return this;
    }
    
    /**
     * Verify that InitiateUpload was called.
     *
     * @return this instance for method chaining
     */
    public ChunkedUploadFlowHelper verifyInitiateUploadCalled() {
        verifier.verifyMethodCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "InitiateUpload"
        );
        return this;
    }
    
    /**
     * Verify that InitiateUpload was called with a specific request.
     *
     * @param expectedRequest The expected request
     * @return this instance for method chaining
     */
    public ChunkedUploadFlowHelper verifyInitiateUploadCalledWith(InitiateUploadRequest expectedRequest) {
        verifier.verifyMethodCalledWith(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "InitiateUpload",
            expectedRequest
        );
        return this;
    }
    
    /**
     * Verify that UploadChunk was called a specific number of times.
     *
     * @param expectedCount The expected number of calls
     * @return this instance for method chaining
     */
    public ChunkedUploadFlowHelper verifyUploadChunkCalled(int expectedCount) {
        verifier.verifyMethodCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "UploadChunk",
            expectedCount
        );
        return this;
    }
    
    /**
     * Verify that GetUploadStatus was called.
     *
     * @return this instance for method chaining
     */
    public ChunkedUploadFlowHelper verifyGetUploadStatusCalled() {
        verifier.verifyMethodCalled(
            "ai.pipestream.repository.filesystem.upload.NodeUploadService",
            "GetUploadStatus"
        );
        return this;
    }
    
    /**
     * Get the underlying RepositoryServiceMock for advanced usage.
     *
     * @return the RepositoryServiceMock instance
     */
    public RepositoryServiceMock getRepositoryMock() {
        return repositoryMock;
    }
    
    /**
     * Get the underlying WireMockGrpcVerifier for advanced usage.
     *
     * @return the WireMockGrpcVerifier instance
     */
    public WireMockGrpcVerifier getVerifier() {
        return verifier;
    }
    
    /**
     * Reset all mocks and verification state.
     *
     * @return this instance for method chaining
     */
    public ChunkedUploadFlowHelper reset() {
        uploadStates.clear();
        verifier.resetRequests();
        return this;
    }
    
    /**
     * Simple data class to hold nodeId and uploadId.
     */
    public static class UploadIds {
        public final String nodeId;
        public final String uploadId;
        
        public UploadIds(String nodeId, String uploadId) {
            this.nodeId = nodeId;
            this.uploadId = uploadId;
        }
    }
}

