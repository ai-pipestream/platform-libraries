package ai.pipestream.grpc.wiremock;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.wiremock.grpc.dsl.WireMockGrpcService;
import ai.pipestream.repository.filesystem.upload.*;

import static ai.pipestream.grpc.wiremock.WireMockGrpcCompat.*;

/**
 * Ready-to-use mock utilities for the Repository Service (NodeUploadService).
 * Uses standard gRPC mocking that works with both standard and Mutiny clients.
 */
@SuppressWarnings("UnusedReturnValue")
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
     * <p>
     * Matches requests with the specified chunk number to ensure correct responses
     * when multiple chunks are uploaded.
     *
     * @param nodeId The node ID
     * @param chunkNumber The chunk number to match
     * @return this instance for method chaining
     */
    public RepositoryServiceMock mockUploadChunk(String nodeId, int chunkNumber) {
        // Build the protobuf request directly with the fields we need to match on.
        // We match PRECISELY on nodeId and chunkNumber.
        //
        // We control the protobuf structure, so we build it directly with the exact fields
        // we care about. The actual requests will include additional fields (uploadId, data, isLast),
        // which we allow via equalToJson with ignoreExtraElements=true.
        //
        // Known Limitation: WireMock's equalToJson with ignoreExtraElements=true has a bug
        // (https://github.com/wiremock/wiremock/issues/1230) that can cause unreliable matching
        // when multiple stubs exist for the same method. Each stub has a unique combination of
        // nodeId + chunkNumber, which should allow WireMock to distinguish them, but the bug
        // can still cause issues. This is a WireMock limitation, not a limitation of our approach.
        //
        // The protobuf structure ensures chunkNumber is serialized as a string in JSON,
        // which matches what gRPC clients send.
        UploadChunkRequest expectedRequest = UploadChunkRequest.newBuilder()
            .setNodeId(nodeId)
            .setChunkNumber(chunkNumber)
            .build();
        
        // Note: WireMock's gRPC extension GrpcStubMappingBuilder doesn't support atPriority(),
        // so we can't control stub evaluation order. This is a limitation when working around
        // WireMock bug #1230 with ignoreExtraElements and multiple stubs.
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
     * Mock successful chunk upload using a dynamic JSON template.
     * <p>
     * This method uses a single stub with JSON templating to handle all chunks dynamically,
     * avoiding the WireMock bug #1230 with multiple stubs and ignoreExtraElements.
     * <p>
     * The response dynamically extracts the chunkNumber from the request and returns it,
     * and sets isFileComplete=true for the last chunk.
     *
     * @param nodeId The node ID to match
     * @param totalChunks The total number of chunks (used to determine if a chunk is the last one)
     * @return this instance for method chaining
     */
    public RepositoryServiceMock mockUploadChunkDynamic(String nodeId, int totalChunks) {
        // Use JSON template to dynamically handle ALL chunks with a SINGLE stub.
        // This completely avoids WireMock bug #1230 with multiple stubs and ignoreExtraElements.
        // 
        // IMPORTANT: This stub matches ANY UploadChunk request (no request matching).
        // The template extracts nodeId, chunkNumber, and isLast from the request dynamically.
        // 
        // The isFileComplete field is set based on the isLast field from the request,
        // which accurately reflects whether this is the final chunk.
        // If isLast is not set in the request, defaults to false.
        String nodeIdPath = "{{jsonPath request.body '$.nodeId'}}";
        String chunkNumberPath = "{{jsonPath request.body '$.chunkNumber'}}";
        
        // Use Handlebars conditional to handle isLast: if present and true, return true; otherwise false
        // Handlebars jsonPath returns empty string for missing fields, so we check for non-empty and 'true'
        String isFileCompleteTemplate = "{{#if (eq (jsonPath request.body '$.isLast') true)}}true{{else}}false{{/if}}";
        
        // Single dynamic stub for all chunks - extracts values from request
        // Use isLast from request to determine isFileComplete (more accurate than comparing chunk numbers)
        String dynamicTemplate = String.format(
            "{ " +
            "\"nodeId\": \"%s\", " +
            "\"chunkNumber\": %s, " +
            "\"state\": \"UPLOAD_STATE_UPLOADING\", " +
            "\"bytesUploaded\": 0, " +
            "\"isFileComplete\": %s " +
            "}",
            nodeIdPath, chunkNumberPath, isFileCompleteTemplate
        );
        
        // No request matching - matches ANY UploadChunk request.
        // The template extracts nodeId, chunkNumber, and isLast from the request dynamically.
        mockService.stubFor(
            method("UploadChunk")
                .willReturn(jsonTemplate(dynamicTemplate))
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
     * Mock upload initiation failure with INVALID_ARGUMENT status.
     *
     * @param errorMessage The error message to return
     * @return this instance for method chaining
     */
    public RepositoryServiceMock mockInitiateUploadInvalidArgument(String errorMessage) {
        mockService.stubFor(
            method("InitiateUpload")
                .willReturn(org.wiremock.grpc.dsl.WireMockGrpc.Status.INVALID_ARGUMENT, errorMessage)
        );
        return this;
    }

    /**
     * Mock upload initiation failure with NOT_FOUND status (e.g., drive or connector not found).
     *
     * @param errorMessage The error message to return
     * @return this instance for method chaining
     */
    public RepositoryServiceMock mockInitiateUploadNotFound(String errorMessage) {
        mockService.stubFor(
            method("InitiateUpload")
                .willReturn(org.wiremock.grpc.dsl.WireMockGrpc.Status.NOT_FOUND, errorMessage)
        );
        return this;
    }

    /**
     * Mock upload initiation failure with RESOURCE_EXHAUSTED status (e.g., quota exceeded).
     *
     * @param errorMessage The error message to return
     * @return this instance for method chaining
     */
    public RepositoryServiceMock mockInitiateUploadResourceExhausted(String errorMessage) {
        mockService.stubFor(
            method("InitiateUpload")
                .willReturn(org.wiremock.grpc.dsl.WireMockGrpc.Status.RESOURCE_EXHAUSTED, errorMessage)
        );
        return this;
    }

    /**
     * Mock upload initiation failure with INTERNAL status.
     *
     * @param errorMessage The error message to return
     * @return this instance for method chaining
     */
    public RepositoryServiceMock mockInitiateUploadInternalError(String errorMessage) {
        mockService.stubFor(
            method("InitiateUpload")
                .willReturn(org.wiremock.grpc.dsl.WireMockGrpc.Status.INTERNAL, errorMessage)
        );
        return this;
    }

    /**
     * Mock chunk upload failure with INTERNAL status.
     * Matches any UploadChunk request (no request matching required).
     *
     * @param nodeId The node ID (for documentation purposes, not used in matching)
     * @param errorMessage The error message to return
     * @return this instance for method chaining
     */
    public RepositoryServiceMock mockUploadChunkFailed(String nodeId, String errorMessage) {
        mockService.stubFor(
            method("UploadChunk")
                .willReturn(org.wiremock.grpc.dsl.WireMockGrpc.Status.INTERNAL, errorMessage)
        );
        return this;
    }

    /**
     * Mock chunk upload failure with NOT_FOUND status (upload doesn't exist).
     * Matches any UploadChunk request (no request matching required).
     *
     * @param nodeId The node ID (for documentation purposes, not used in matching)
     * @param errorMessage The error message to return
     * @return this instance for method chaining
     */
    public RepositoryServiceMock mockUploadChunkNotFound(String nodeId, String errorMessage) {
        mockService.stubFor(
            method("UploadChunk")
                .willReturn(org.wiremock.grpc.dsl.WireMockGrpc.Status.NOT_FOUND, errorMessage)
        );
        return this;
    }

    /**
     * Mock upload status retrieval failure with NOT_FOUND status.
     * Matches any GetUploadStatus request (no request matching required).
     *
     * @param nodeId The node ID (for documentation purposes, not used in matching)
     * @return this instance for method chaining
     */
    public RepositoryServiceMock mockGetUploadStatusNotFound(String nodeId) {
        mockService.stubFor(
            method("GetUploadStatus")
                .willReturn(org.wiremock.grpc.dsl.WireMockGrpc.Status.NOT_FOUND,
                    "Upload not found: " + nodeId)
        );
        return this;
    }

    /**
     * Mock upload cancellation failure with FAILED_PRECONDITION status (e.g., already completed).
     * Matches any CancelUpload request (no request matching required).
     *
     * @param nodeId The node ID (for documentation purposes, not used in matching)
     * @param errorMessage The error message to return
     * @return this instance for method chaining
     */
    public RepositoryServiceMock mockCancelUploadFailed(String nodeId, String errorMessage) {
        mockService.stubFor(
            method("CancelUpload")
                .willReturn(org.wiremock.grpc.dsl.WireMockGrpc.Status.FAILED_PRECONDITION, errorMessage)
        );
        return this;
    }

    /**
     * Mock upload cancellation failure with NOT_FOUND status.
     * Matches any CancelUpload request (no request matching required).
     *
     * @param nodeId The node ID (for documentation purposes, not used in matching)
     * @return this instance for method chaining
     */
    public RepositoryServiceMock mockCancelUploadNotFound(String nodeId) {
        mockService.stubFor(
            method("CancelUpload")
                .willReturn(org.wiremock.grpc.dsl.WireMockGrpc.Status.NOT_FOUND,
                    "Upload not found: " + nodeId)
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
     * Setup a complete chunked upload flow with all necessary mocks.
     * <p>
     * This is a convenience method that sets up:
     * - InitiateUpload (returns the provided nodeId and uploadId)
     * - UploadChunk for chunks 1 through totalChunks (with request matching on chunk number)
     * - GetUploadStatus (returns COMPLETED state)
     * <p>
     * Note: The last chunk will have isFileComplete=true in its response.
     *
     * @param nodeId The node ID to use for the upload
     * @param uploadId The upload ID to use for the upload
     * @param totalChunks The total number of chunks that will be uploaded
     * @return this instance for method chaining
     */
    public RepositoryServiceMock setupChunkedUploadFlow(String nodeId, String uploadId, int totalChunks) {
        // Setup InitiateUpload
        mockInitiateUpload(nodeId, uploadId);
        
        // Use dynamic JSON template approach to avoid WireMock bug #1230 with multiple stubs.
        // This creates a single stub that handles all chunks dynamically.
        mockUploadChunkDynamic(nodeId, totalChunks);
        
        // Setup GetUploadStatus to return COMPLETED
        mockGetUploadStatus(nodeId, UploadState.UPLOAD_STATE_COMPLETED);
        
        return this;
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

