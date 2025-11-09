package ai.pipestream.grpc.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base test class that provides easy access to all service mocks.
 * 
 * <p>Usage:
 * <pre>{@code
 * @QuarkusTest
 * @TestProfile(MockGrpcProfile.class)
 * public class MyTest extends WireMockTestBase {
 *     @Test
 *     public void testSomething() {
 *         // Setup mocks
 *         mocks.accountManager().mockGetAccount("test", "Test", "Desc", true);
 *         mocks.platformRegistration().mockServiceRegistration();
 *         
 *         // Your test code here
 *     }
 * }
 * }</pre>
 */
public abstract class WireMockTestBase {

    /**
     * Protected constructor to prevent direct instantiation.
     */
    protected WireMockTestBase() {
    }
    
    /**
     * Injected WireMock server instance for test configuration.
     */
    @InjectWireMock
    protected WireMockServer wireMockServer;
    
    /**
     * Service mocks factory for easy access to all service mocks.
     * Initialized in setUpMocks().
     */
    protected ServiceMocks mocks;
    
    /**
     * Initialize the service mocks factory.
     * Called automatically before each test.
     */
    @BeforeEach
    protected void setUpMocks() {
        mocks = new ServiceMocks(wireMockServer);
    }
}
