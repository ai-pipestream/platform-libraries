package ai.pipestream.grpc.wiremock;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.wiremock.grpc.dsl.WireMockGrpcService;
import ai.pipestream.repository.filesystem.upload.*;

import static ai.pipestream.grpc.wiremock.WireMockGrpcCompat.*;

/**
 * Ready-to-use mock utilities for the Repository Service (NodeUploadService).
 * Uses standard gRPC mocking that works with both standard and Mutiny clients.
 */
public class RepositoryServiceMock {

    private final WireMockGrpcService mockService;

    /**
     * Creates a repository service mock for the given WireMock port.
     *
     * @param wireMockPort The port where WireMock is running
     */
    public RepositoryServiceMock(int wireMockPort) {
        this.mockService = new WireMockGrpcService(
            new WireMock(wireMockPort), 
            "ai.pipestream.repository.filesystem.upload.NodeUploadService"
        );
    }

    /**
     * Mock successful upload initiation.
     *
     * @param nodeId The node ID to return
     * @param uploadId The upload ID to return
     * @return this instance for method chaining
     */
    public RepositoryServiceMock mockInitiateUpload(String nodeId, String uploadId) {
        mockService.stubFor(
            method("InitiateUpload")
                .willReturn(message(
                    InitiateUploadResponse.newBuilder()
                        .setNodeId(nodeId)
                        .setUploadId(uploadId)
                        .setState(UploadState.UPLOAD_STATE_PENDING)
                        .setCreatedAtEpochMs(System.currentTimeMillis())
                        .setIsUpdate(false)
                        .build()
                ))
        );
        
        return this;
    }

    /**
     * Mock successful upload initiation with specific request matching.
     *
     * @param nodeId The node ID to return
     * @param uploadId The upload ID to return
     * @param expectedRequest The expected request to match
     * @return this instance for method chaining
     */
    public RepositoryServiceMock mockInitiateUpload(String nodeId, String uploadId, InitiateUploadRequest expectedRequest) {
        mockService.stubFor(
            method("InitiateUpload")
                .withRequestMessage(equalToMessage(expectedRequest))
                .willReturn(message(
                    InitiateUploadResponse.newBuilder()
                        .setNodeId(nodeId)
                        .setUploadId(uploadId)
                        .setState(UploadState.UPLOAD_STATE_PENDING)
                        .setCreatedAtEpochMs(System.currentTimeMillis())
                        .setIsUpdate(false)
                        .build()
                ))
        );
        
        return this;
    }

    /**
     * Mock upload initiation with default values.
     * Creates a simple successful response with generated IDs.
     *
     * @return this instance for method chaining
     */
    public RepositoryServiceMock mockInitiateUpload() {
        return mockInitiateUpload(
            "test-node-id-" + System.currentTimeMillis(),
            "test-upload-id-" + System.currentTimeMillis()
        );
    }

    /**
     * Mock successful chunk upload.
     *
     * @param nodeId The node ID
     * @param chunkNumber The chunk number
     * @return this instance for method chaining
     */
    public RepositoryServiceMock mockUploadChunk(String nodeId, int chunkNumber) {
        mockService.stubFor(
            method("UploadChunk")
                .willReturn(message(
                    UploadChunkResponse.newBuilder()
                        .setNodeId(nodeId)
                        .setChunkNumber(chunkNumber)
                        .setState(UploadState.UPLOAD_STATE_UPLOADING)
                        .setBytesUploaded(0)
                        .setIsFileComplete(false)
                        .build()
                ))
        );
        
        return this;
    }

    /**
     * Mock successful chunk upload with specific request matching.
     *
     * @param nodeId The node ID
     * @param chunkNumber The chunk number
     * @param expectedRequest The expected request to match
     * @return this instance for method chaining
     */
    public RepositoryServiceMock mockUploadChunk(String nodeId, int chunkNumber, UploadChunkRequest expectedRequest) {
        mockService.stubFor(
            method("UploadChunk")
                .withRequestMessage(equalToMessage(expectedRequest))
                .willReturn(message(
                    UploadChunkResponse.newBuilder()
                        .setNodeId(nodeId)
                        .setChunkNumber(chunkNumber)
                        .setState(UploadState.UPLOAD_STATE_UPLOADING)
                        .setBytesUploaded(0)
                        .setIsFileComplete(false)
                        .build()
                ))
        );
        
        return this;
    }

    /**
     * Mock upload status retrieval.
     *
     * @param nodeId The node ID
     * @param state The upload state
     * @return this instance for method chaining
     */
    public RepositoryServiceMock mockGetUploadStatus(String nodeId, UploadState state) {
        mockService.stubFor(
            method("GetUploadStatus")
                .willReturn(message(
                    GetUploadStatusResponse.newBuilder()
                        .setNodeId(nodeId)
                        .setState(state)
                        .setBytesUploaded(0)
                        .setTotalBytes(0)
                        .setUpdatedAtEpochMs(System.currentTimeMillis())
                        .build()
                ))
        );
        
        return this;
    }

    /**
     * Mock successful upload cancellation.
     *
     * @param nodeId The node ID
     * @return this instance for method chaining
     */
    public RepositoryServiceMock mockCancelUpload(String nodeId) {
        mockService.stubFor(
            method("CancelUpload")
                .willReturn(message(
                    CancelUploadResponse.newBuilder()
                        .setNodeId(nodeId)
                        .setSuccess(true)
                        .setMessage("Upload cancelled successfully")
                        .build()
                ))
        );
        
        return this;
    }

    /**
     * Setup default mocks for basic operations.
     * 
     * <p>Configures default InitiateUpload mock.
     *
     * @return this instance for method chaining
     */
    public RepositoryServiceMock setupDefaults() {
        return mockInitiateUpload();
    }

    /**
     * Get the underlying WireMockGrpcService for advanced usage.
     *
     * @return the WireMockGrpcService instance
     */
    public WireMockGrpcService getService() {
        return mockService;
    }
}

