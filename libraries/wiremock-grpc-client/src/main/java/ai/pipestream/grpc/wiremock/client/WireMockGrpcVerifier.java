package ai.pipestream.grpc.wiremock.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.google.protobuf.MessageOrBuilder;

import static ai.pipestream.grpc.wiremock.client.WireMockGrpcClient.equalToGrpcMessage;

/**
 * Utility class for verifying gRPC calls made to WireMock.
 * <p>
 * Provides convenient methods to verify that specific gRPC methods were called
 * with expected requests, and to check call counts.
 * <p>
 * Based on WireMock gRPC documentation: https://wiremock.org/docs/grpc/
 * gRPC requests use URL path format: /&lt;fully-qualified service name&gt;/&lt;method name&gt;
 */
public class WireMockGrpcVerifier {
    
    /**
     * Creates a verifier.
     */
    public WireMockGrpcVerifier() {
        // No-op
    }
    
    /**
     * Verify that a gRPC method was called at least once.
     *
     * @param serviceName The fully-qualified service name (e.g., "ai.pipestream.repository.filesystem.upload.NodeUploadService")
     * @param methodName The method name (e.g., "InitiateUpload")
     */
    public void verifyMethodCalled(String serviceName, String methodName) {
        int count = getRequestCount(serviceName, methodName);
        if (count < 1) {
            throw new AssertionError(
                String.format("Expected at least 1 call to %s/%s, but got %d", serviceName, methodName, count)
            );
        }
    }
    
    /**
     * Verify that a gRPC method was called a specific number of times.
     *
     * @param serviceName The fully-qualified service name
     * @param methodName The method name
     * @param expectedCount The expected number of calls
     */
    public void verifyMethodCalled(String serviceName, String methodName, int expectedCount) {
        int actualCount = getRequestCount(serviceName, methodName);
        if (actualCount != expectedCount) {
            throw new AssertionError(
                String.format("Expected %d calls to %s/%s, but got %d", expectedCount, serviceName, methodName, actualCount)
            );
        }
    }
    
    /**
     * Verify that a gRPC method was called with a specific request message.
     *
     * @param serviceName The fully-qualified service name
     * @param methodName The method name
     * @param expectedRequest The expected request message
     */
    public void verifyMethodCalledWith(String serviceName, String methodName, MessageOrBuilder expectedRequest) {
        String urlPath = "/" + serviceName + "/" + methodName;
        RequestPatternBuilder pattern = RequestPatternBuilder.newRequestPattern()
            .withUrl(WireMock.urlPathEqualTo(urlPath))
            .withRequestBody(equalToGrpcMessage(expectedRequest)); // Use equalToGrpcMessage from WireMockGrpcClient
        
        int count = WireMock.findAll(pattern).size();
        if (count < 1) {
            throw new AssertionError(
                String.format("Expected at least 1 call to %s/%s with matching request, but got %d", 
                    serviceName, methodName, count)
            );
        }
    }
    
    /**
     * Verify that a gRPC method was never called.
     *
     * @param serviceName The fully-qualified service name
     * @param methodName The method name
     */
    public void verifyMethodNeverCalled(String serviceName, String methodName) {
        int count = getRequestCount(serviceName, methodName);
        if (count != 0) {
            throw new AssertionError(
                String.format("Expected 0 calls to %s/%s, but got %d", serviceName, methodName, count)
            );
        }
    }
    
    /**
     * Reset all request verification state.
     * <p>
     * This clears the request journal, so previous verifications won't affect future ones.
     */
    public void resetRequests() {
        WireMock.resetRequests();
    }
    
    /**
     * Get the count of requests made to a specific method.
     * <p>
     * Uses WireMock's request journal to count matching requests.
     *
     * @param serviceName The fully-qualified service name
     * @param methodName The method name
     * @return The number of requests made
     */
    public int getRequestCount(String serviceName, String methodName) {
        String urlPath = "/" + serviceName + "/" + methodName;
        RequestPatternBuilder pattern = RequestPatternBuilder.newRequestPattern()
            .withUrl(WireMock.urlPathEqualTo(urlPath));
        
        return WireMock.findAll(pattern).size();
    }
}