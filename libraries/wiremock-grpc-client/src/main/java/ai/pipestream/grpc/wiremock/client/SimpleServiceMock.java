package ai.pipestream.grpc.wiremock.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.protobuf.Empty;

import static ai.pipestream.grpc.wiremock.client.WireMockGrpcClient.*;

/**
 * Simple service mock for basic gRPC operations.
 * 
 * <p>This is a placeholder that can be extended for specific services.
 * Provides basic health check and ping operations that are common across services.
 */
public class SimpleServiceMock {
    
    private final String serviceName;
    
    /**
     * Creates a simple service mock for the given WireMock port.
     *
     * @param serviceName The gRPC service name to mock (e.g. "ai.pipestream.MyService")
     */
    public SimpleServiceMock(String serviceName) {
        this.serviceName = serviceName;
    }
    
    /**
     * Mock a simple health check.
     *
     * @return this instance for method chaining
     */
    public SimpleServiceMock mockHealthCheck() {
        WireMock.stubFor(
            grpcStubFor(serviceName, "HealthCheck")
                .willReturn(aGrpcResponseWith(Empty.getDefaultInstance()))
        );
        return this;
    }
    
    /**
     * Mock a simple ping operation.
     *
     * @return this instance for method chaining
     */
    public SimpleServiceMock mockPing() {
        WireMock.stubFor(
            grpcStubFor(serviceName, "Ping")
                .willReturn(aGrpcResponseWith(Empty.getDefaultInstance()))
        );
        return this;
    }
    
    /**
     * Setup default mocks for basic operations.
     * 
     * <p>Configures health check and ping mocks.
     *
     * @return this instance for method chaining
     */
    public SimpleServiceMock setupDefaults() {
        return mockHealthCheck()
               .mockPing();
    }
}