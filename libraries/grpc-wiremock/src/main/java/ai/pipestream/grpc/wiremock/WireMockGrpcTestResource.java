package ai.pipestream.grpc.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.wiremock.grpc.GrpcExtensionFactory;

import java.util.Map;

/**
 * Quarkus test resource that starts WireMock with gRPC support and configures the gRPC clients to use it.
 */
public class WireMockGrpcTestResource implements QuarkusTestResourceLifecycleManager {

    /**
     * The WireMock server instance.
     */
    private WireMockServer wireMockServer;

    /**
     * Default constructor.
     */
    public WireMockGrpcTestResource() {
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Starts WireMock with gRPC support and configures gRPC client ports
     * to use the WireMock server.
     */
    @Override
    public Map<String, String> start() {
        wireMockServer = new WireMockServer(
                WireMockConfiguration.wireMockConfig()
                        .dynamicPort()
                        .usingFilesUnderClasspath("META-INF")
                        .extensions(new GrpcExtensionFactory())
        );
        wireMockServer.start();
        
        // Return configuration properties that will be injected into the test
        return Map.of(
                "quarkus.grpc.clients.FilesystemService.port", String.valueOf(wireMockServer.port()),
                "test.wiremock.port", String.valueOf(wireMockServer.port())
        );
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Stops the WireMock server.
     */
    @Override
    public void stop() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }
    
    /**
     * {@inheritDoc}
     * 
     * <p>Injects the WireMock server into test fields annotated with @InjectWireMock.
     */
    @Override
    public void inject(TestInjector testInjector) {
        // Inject the WireMock server into tests that need it
        testInjector.injectIntoFields(
                wireMockServer,
                new TestInjector.AnnotatedAndMatchesType(InjectWireMock.class, WireMockServer.class)
        );
    }
}