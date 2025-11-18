package ai.pipestream.grpc.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.wiremock.grpc.GrpcExtensionFactory;

import java.util.Map;

/**
 * Composite Quarkus test resource for mocking all services needed by connector-intake-service.
 * <p>
 * Starts a single WireMock server with gRPC extension and configures Stork static discovery
 * to route repository-service, connector-service, and account-manager calls to WireMock.
 * <p>
 * This is a convenience resource that combines RepositoryServiceMockTestResource,
 * ConnectorServiceMockTestResource, and AccountManagerMockTestResource into a single
 * resource that shares one WireMock server instance.
 * <p>
 * Usage:
 * <pre>
 * {@code
 * @QuarkusTest
 * @QuarkusTestResource(ConnectorIntakeTestResource.class)
 * public class MyTest {
 *     @InjectWireMock
 *     WireMockServer wireMockServer;
 *
 *     @BeforeEach
 *     void setup() {
 *         new RepositoryServiceMock(wireMockServer.port())
 *             .mockInitiateUpload("test-node-id", "test-upload-id");
 *         new ConnectorServiceMock(wireMockServer.port())
 *             .mockValidateApiKey("test-connector", "test-api-key", "test-account");
 *         new AccountManagerMock(wireMockServer.port())
 *             .mockGetAccount("test-account", "Test Account", "Description", true);
 *     }
 * }
 * }
 * </pre>
 */
public class ConnectorIntakeTestResource implements QuarkusTestResourceLifecycleManager {

    /**
     * The WireMock server instance.
     */
    private WireMockServer wireMockServer;

    /**
     * Default constructor.
     */
    public ConnectorIntakeTestResource() {
    }

    @Override
    public Map<String, String> start() {
        // Start WireMock with gRPC extension
        wireMockServer = new WireMockServer(
            WireMockConfiguration.wireMockConfig()
                .dynamicPort()
                .usingFilesUnderClasspath("META-INF")
                .extensions(new GrpcExtensionFactory())
        );
        wireMockServer.start();

        int mockPort = wireMockServer.port();

        // Return Stork configuration to route all services to WireMock
        return Map.ofEntries(
            // Stork static discovery for repository-service
            Map.entry("quarkus.stork.repository-service.service-discovery.type", "static"),
            Map.entry("quarkus.stork.repository-service.service-discovery.address-list", "localhost:" + mockPort),

            // Stork static discovery for connector-service
            Map.entry("quarkus.stork.connector-service.service-discovery.type", "static"),
            Map.entry("quarkus.stork.connector-service.service-discovery.address-list", "localhost:" + mockPort),

            // Stork static discovery for account-manager
            Map.entry("quarkus.stork.account-manager.service-discovery.type", "static"),
            Map.entry("quarkus.stork.account-manager.service-discovery.address-list", "localhost:" + mockPort),

            // System property for injecting WireMock server
            Map.entry("test.wiremock.port", String.valueOf(mockPort)),

            // Disable service registration in tests
            Map.entry("service.registration.enabled", "false")
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
        // Inject WireMock server into test fields annotated with @InjectWireMock
        testInjector.injectIntoFields(
            wireMockServer,
            new TestInjector.AnnotatedAndMatchesType(InjectWireMock.class, WireMockServer.class)
        );
    }
}

