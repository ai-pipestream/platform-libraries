package ai.pipestream.grpc.wiremock;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.wiremock.grpc.GrpcExtensionFactory;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Base test class that provides WireMock gRPC mocking for services that require external gRPC connections.
 * This allows unit tests to run without actual external services while still testing service integration logic.
 */
public abstract class WireMockGrpcTestBase {

    /**
     * Protected constructor to prevent direct instantiation.
     */
    protected WireMockGrpcTestBase() {
    }

    /**
     * WireMock extension providing gRPC support for testing.
     */
    @RegisterExtension
    public static WireMockExtension wireMock =
            WireMockExtension.newInstance()
                    .options(
                            wireMockConfig()
                                    .dynamicPort()
                                    .extensions(new GrpcExtensionFactory())
                                    .usingFilesUnderClasspath("META-INF")
                    )
                    .build();

    /**
     * Configures gRPC clients to use WireMock server before all tests.
     */
    @BeforeAll
    public static void configureGrpcClients() {
        // Configure common gRPC client ports to use WireMock
        int port = wireMock.getPort();
        System.setProperty("quarkus.grpc.clients.FilesystemService.port", String.valueOf(port));
        System.setProperty("quarkus.grpc.clients.PlatformRegistration.port", String.valueOf(port));
        System.setProperty("test.wiremock.port", String.valueOf(port));
        
        System.out.println("✅ WireMock gRPC testing framework initialized on port: " + port);
    }

    /**
     * Get the WireMock port for configuring gRPC clients in tests.
     *
     * @return the WireMock server port
     */
    protected static int getWireMockPort() {
        return wireMock.getPort();
    }
}