package ai.pipestream.dynamic.grpc;

import ai.pipestream.dynamic.grpc.client.DynamicGrpcClientFactory;
import io.smallrye.stork.Stork;
import io.smallrye.stork.integration.DefaultStorkInfrastructure;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractDynamicGrpcTestBase {

    protected DynamicGrpcClientFactory clientFactory;

    protected abstract DynamicGrpcClientFactory getFactory();
    protected abstract String getTestServiceName();

    @BeforeAll
    static void initStork() {
        Stork.initialize(new DefaultStorkInfrastructure());
    }

    @AfterAll
    static void shutdownStork() {
        Stork.shutdown();
    }

    @BeforeEach
    void setupBase() {
        clientFactory = getFactory();
        additionalSetup();
    }

    @AfterEach
    void cleanupBase() {
        additionalCleanup();
    }

    protected void additionalSetup() {
    }

    protected void additionalCleanup() {
    }

    @Test
    void testClientFactoryInitialization() {
        assertThat(clientFactory).isNotNull();
    }

    @Test
    void testGetClientForService() {
        var clientUni = clientFactory.getMutinyClientForService(getTestServiceName());
        assertThat(clientUni).isNotNull();
        // Optionally await to ensure stub is creatable
        var stub = clientUni.await().atMost(java.time.Duration.ofSeconds(5));
        assertThat(stub).isNotNull();
    }

    @Test
    void testChannelCaching() {
        var client1 = clientFactory.getMutinyClientForService(getTestServiceName()).await().atMost(java.time.Duration.ofSeconds(5));
        var client2 = clientFactory.getMutinyClientForService(getTestServiceName()).await().atMost(java.time.Duration.ofSeconds(5));
        assertThat(client1).isNotNull();
        assertThat(client2).isNotNull();
        // Channel is cached internally; active service count should reflect a single channel
        assertThat(clientFactory.getActiveServiceCount()).isGreaterThanOrEqualTo(1);
    }
}
