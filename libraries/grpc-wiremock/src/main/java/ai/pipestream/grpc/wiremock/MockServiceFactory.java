package ai.pipestream.grpc.wiremock;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import ai.pipestream.platform.registration.PlatformRegistrationGrpc;

/**
 * Factory for creating mock gRPC services.
 * 
 * This allows us to easily switch between real and mock implementations
 * in tests without changing the application code.
 */
public class MockServiceFactory {

    /**
     * Default constructor.
     */
    public MockServiceFactory() {
    }

    private static DirectWireMockGrpcServer mockServer;
    private static ManagedChannel mockChannel;

    /**
     * Start the mock platform registration service.
     * This should be called in test setup.
     */
    public static void startMockPlatformRegistrationService() {
        try {
            if (mockServer == null) {
                mockServer = new DirectWireMockGrpcServer(0);
                mockServer.start();
                
                mockChannel = ManagedChannelBuilder.forAddress("localhost", mockServer.getGrpcPort())
                    .usePlaintext()
                    .build();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to start mock platform registration service", e);
        }
    }

    /**
     * Stop the mock platform registration service.
     * This should be called in test cleanup.
     */
    public static void stopMockPlatformRegistrationService() {
        try {
            if (mockChannel != null) {
                mockChannel.shutdown();
                mockChannel = null;
            }
            if (mockServer != null) {
                mockServer.stop();
                mockServer = null;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to stop mock platform registration service", e);
        }
    }

    /**
     * Get the mock platform registration service stub.
     * 
     * <p>This returns the mock implementation when in test mode.
     *
     * @return the platform registration stub
     * @throws IllegalStateException if the mock service has not been started
     */
    public static PlatformRegistrationGrpc.PlatformRegistrationStub getPlatformRegistrationStub() {
        if (mockServer != null && mockChannel != null) {
            return PlatformRegistrationGrpc.newStub(mockChannel);
        }
        throw new IllegalStateException("Mock platform registration service not started. Call startMockPlatformRegistrationService() first.");
    }

    /**
     * Get the mock platform registration service blocking stub.
     * 
     * <p>This returns the mock implementation when in test mode.
     *
     * @return the platform registration blocking stub
     * @throws IllegalStateException if the mock service has not been started
     */
    public static PlatformRegistrationGrpc.PlatformRegistrationBlockingStub getPlatformRegistrationBlockingStub() {
        if (mockServer != null && mockChannel != null) {
            return PlatformRegistrationGrpc.newBlockingStub(mockChannel);
        }
        throw new IllegalStateException("Mock platform registration service not started. Call startMockPlatformRegistrationService() first.");
    }

    /**
     * Check if the mock service is running.
     *
     * @return true if the mock service is running, false otherwise
     */
    public static boolean isMockServiceRunning() {
        return mockServer != null && mockChannel != null;
    }

    /**
     * Get the mock server instance for advanced configuration.
     *
     * @return the DirectWireMockGrpcServer instance, or null if not started
     */
    public static DirectWireMockGrpcServer getMockServer() {
        return mockServer;
    }
}