package ai.pipestream.grpc.wiremock.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import ai.pipestream.platform.registration.PlatformRegistrationGrpc;
import ai.pipestream.platform.registration.RegistrationEvent;
import ai.pipestream.platform.registration.EventType;
import ai.pipestream.platform.registration.ServiceListResponse;
import ai.pipestream.platform.registration.ModuleListResponse;
import com.google.protobuf.Empty;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PlatformRegistrationMock to verify the mock stubs work correctly.
 * <p>
 * Note: This tests the unary-style simulation of registration events,
 * as the WireMock gRPC extension does not support streaming response sequences.
 */
@QuarkusTest
@QuarkusTestResource(WireMockServerTestResource.class)
public class PlatformRegistrationMockTest {

    private static final Logger LOG = Logger.getLogger(PlatformRegistrationMockTest.class);

    @ConfigProperty(name = "wiremock.url")
    String wiremockUrl;

    private ManagedChannel channel;
    private PlatformRegistrationGrpc.PlatformRegistrationBlockingStub registrationService;
    private PlatformRegistrationMock platformMock;

    @BeforeEach
    void setUp() {
        LOG.info("WireMock URL: " + wiremockUrl);

        // Extract HTTP port from wiremock.url
        int httpPort = Integer.parseInt(wiremockUrl.substring(wiremockUrl.lastIndexOf(":") + 1));
        
        // Configure WireMock client
        WireMock.configureFor("localhost", httpPort);
        WireMock.reset();

        // Initialize the mock helper
        platformMock = new PlatformRegistrationMock();

        // Create gRPC client
        channel = ManagedChannelBuilder.forAddress("localhost", httpPort)
            .usePlaintext()
            .build();
        registrationService = PlatformRegistrationGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Test
    void testMockServiceRegistration() {
        // Setup mock
        platformMock.mockServiceRegistration();

        // Call
        // Note: RegisterService is a streaming method, but we are using a blocking stub
        // which returns an iterator. We just check the first response.
        var responseIterator = registrationService.registerService(
            ai.pipestream.platform.registration.ServiceRegistrationRequest.newBuilder()
                .setServiceName("test-service")
                .build()
        );

        assertTrue(responseIterator.hasNext());
        RegistrationEvent event = responseIterator.next();

        // Verify
        assertThat(event.getEventType(), is(EventType.STARTED));
        assertThat(event.getMessage(), containsString("streaming simulation"));
    }

    @Test
    void testMockServiceRegistrationCompleted() {
        // Setup mock
        platformMock.mockServiceRegistrationCompleted();

        // Call
        var responseIterator = registrationService.registerService(
            ai.pipestream.platform.registration.ServiceRegistrationRequest.newBuilder()
                .setServiceName("test-service")
                .build()
        );

        assertTrue(responseIterator.hasNext());
        RegistrationEvent event = responseIterator.next();

        // Verify
        assertThat(event.getEventType(), is(EventType.COMPLETED));
        assertThat(event.getMessage(), containsString("successfully"));
    }

    @Test
    void testMockModuleRegistration() {
        // Setup mock
        platformMock.mockModuleRegistration();

        // Call
        var responseIterator = registrationService.registerModule(
            ai.pipestream.platform.registration.ModuleRegistrationRequest.newBuilder()
                .setModuleName("test-module")
                .build()
        );

        assertTrue(responseIterator.hasNext());
        RegistrationEvent event = responseIterator.next();

        // Verify
        assertThat(event.getEventType(), is(EventType.STARTED));
        assertThat(event.getMessage(), containsString("streaming simulation"));
    }

    @Test
    void testMockListServices() {
        // Setup mock
        platformMock.mockListServices();

        // Call
        ServiceListResponse response = registrationService.listServices(Empty.getDefaultInstance());

        // Verify
        assertThat(response, is(notNullValue()));
        assertThat(response.getTotalCount(), is(2));
        assertThat(response.getServicesCount(), is(2));
        
        var service1 = response.getServices(0);
        assertThat(service1.getServiceName(), is("repository-service"));
        assertThat(service1.getIsHealthy(), is(true));
    }

    @Test
    void testMockListModules() {
        // Setup mock
        platformMock.mockListModules();

        // Call
        ModuleListResponse response = registrationService.listModules(Empty.getDefaultInstance());

        // Verify
        assertThat(response, is(notNullValue()));
        assertThat(response.getTotalCount(), is(2));
        assertThat(response.getModulesCount(), is(2));
        
        var module1 = response.getModules(0);
        assertThat(module1.getModuleName(), is("parser"));
        assertThat(module1.getInputFormat(), is("text/plain"));
    }
}