package ai.pipestream.grpc.wiremock.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import ai.pipestream.repository.RepositoryServiceGrpc;
import ai.pipestream.repository.SaveDocumentResponse;
import ai.pipestream.repository.SaveDocumentRequest;

import static ai.pipestream.grpc.wiremock.client.WireMockGrpcClient.*;

/**
 * Ready-to-use mock utilities for the Repository Service.
 */
public class RepositoryServiceMock {

    private static final String SERVICE_NAME = "ai.pipestream.repository.RepositoryService";

    public RepositoryServiceMock() {
    }

    /**
     * Mock successful document save.
     *
     * @param docId The document ID
     * @return this instance for method chaining
     */
    public RepositoryServiceMock mockSaveDocument(String docId) {
        WireMock.stubFor(
            grpcStubFor(SERVICE_NAME, "SaveDocument")
                .willReturn(aGrpcResponseWith(
                    SaveDocumentResponse.newBuilder()
                        .setDocumentId(docId)
                        .setMessage("Success")
                        .build()
                ))
        );
        return this;
    }
}