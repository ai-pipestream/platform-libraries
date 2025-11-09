package ai.pipestream.dynamic.grpc;

import ai.pipestream.dynamic.grpc.client.DynamicGrpcClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.stork.Stork;
import io.smallrye.stork.api.ServiceDefinition;
import io.smallrye.stork.integration.DefaultStorkInfrastructure;
import io.smallrye.stork.spi.config.SimpleServiceConfig;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ExampleAbstractGetterUnitTest extends AbstractDynamicGrpcTestBase {

    private static final String TEST_SERVICE_NAME = "echo";

    @Override
    protected void additionalSetup() {
        if (Stork.getInstance() != null) {
            Stork.shutdown();
        }
        Stork.initialize(new DefaultStorkInfrastructure());
        Map<String, String> params = Map.of("address-list", "127.0.0.1:49091");
        var discoveryConfig = new SimpleServiceConfig.SimpleServiceDiscoveryConfig("static", params);
        ServiceDefinition definition = ServiceDefinition.of(discoveryConfig);
        Stork.getInstance().defineIfAbsent(TEST_SERVICE_NAME, definition);
    }

    @Inject
    DynamicGrpcClientFactory injectedFactory;

    @Override
    protected DynamicGrpcClientFactory getFactory() {
        return injectedFactory;
    }

    @Override
    protected String getTestServiceName() {
        return TEST_SERVICE_NAME;
    }

    @Test
    void testClientFactoryWithStork() {
        var clientUni = clientFactory.getMutinyClientForService(TEST_SERVICE_NAME);
        assertThat(clientUni).isNotNull();
    }
}
