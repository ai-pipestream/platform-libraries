package ai.pipestream.grpc.wiremock;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Example of how to use the MockServiceFactory in a Quarkus test.
 * 
 * This demonstrates how to integrate mock services with Quarkus tests
 * using the MockServiceTestProfile.
 */
@QuarkusTest
@TestProfile(MockServiceTestProfile.class)
public class QuarkusEchoServiceWithMockTest {

    @BeforeEach
    void setUp() {
        // The MockServiceTestResource should automatically start the mock services
        // when using the MockServiceTestProfile
    }

    @AfterEach
    void tearDown() {
        // The MockServiceTestResource should automatically stop the mock services
    }

    @Test
    void testMockServicesAreAvailable() {
        // Verify that mock services are running
        assertTrue(MockServiceFactory.isMockServiceRunning(),
            "Mock services should be running when using MockServiceTestProfile");
        
        // Verify we can get the mock server
        DirectWireMockGrpcServer mockServer = MockServiceFactory.getMockServer();
        assertNotNull(mockServer, "Mock server should be available");
        assertTrue(mockServer.getGrpcPort() > 0, "Mock server should have a valid port");
        
        // Verify we can get the stub
        assertDoesNotThrow(() -> {
            MockServiceFactory.getPlatformRegistrationStub();
        }, "Should be able to get platform registration stub");
    }

    @Test
    void testEchoServiceCanUseMockRegistration() {
        // This test would demonstrate how the echo service would interact
        // with the mock platform registration service
        
        // In a real implementation, the echo service would be configured
        // to use the mock platform registration service instead of the real one
        
        // The mock service factory provides the same interface as the real service
        // so the echo service code doesn't need to change
        
        assertTrue(MockServiceFactory.isMockServiceRunning(), 
            "Mock services should be available for echo service to use");
    }
}