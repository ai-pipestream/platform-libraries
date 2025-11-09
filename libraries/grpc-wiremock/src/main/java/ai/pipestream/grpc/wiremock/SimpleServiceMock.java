package ai.pipestream.grpc.wiremock;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.wiremock.grpc.dsl.WireMockGrpcService;
import com.google.protobuf.Empty;

import static ai.pipestream.grpc.wiremock.WireMockGrpcCompat.*;

/**
 * Simple service mock for basic gRPC operations.
 * 
 * <p>This is a placeholder that can be extended for specific services.
 * Provides basic health check and ping operations that are common across services.
 */
public class SimpleServiceMock {
    
    private final WireMockGrpcService mockService;
    
    /**
     * Creates a simple service mock for the given WireMock port.
     *
     * @param port The port where WireMock is running
     */
    public SimpleServiceMock(int port) {
        this.mockService = new WireMockGrpcService(
            new WireMock(port), 
            "SimpleService" // Generic service name
        );
    }
    
    /**
     * Mock a simple health check.
     *
     * @return this instance for method chaining
     */
    public SimpleServiceMock mockHealthCheck() {
        mockService.stubFor(
            method("HealthCheck")
                .willReturn(message(Empty.getDefaultInstance()))
        );
        return this;
    }
    
    /**
     * Mock a simple ping operation.
     *
     * @return this instance for method chaining
     */
    public SimpleServiceMock mockPing() {
        mockService.stubFor(
            method("Ping")
                .willReturn(message(Empty.getDefaultInstance()))
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