package ai.pipestream.dynamic.grpc;

import ai.pipestream.dynamic.grpc.client.DynamicGrpcClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.stork.Stork;
import io.smallrye.stork.api.ServiceDefinition;
import io.smallrye.stork.integration.DefaultStorkInfrastructure;
import io.smallrye.stork.spi.config.SimpleServiceConfig;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

import java.util.Map;

@QuarkusTest
class DynamicGrpcClientFactoryUnitTest extends DynamicGrpcClientFactoryTestBase {

    @Inject
    DynamicGrpcClientFactory factory;

    @BeforeEach
    void setupTest() {
        if (Stork.getInstance() != null) {
            Stork.shutdown();
        }
        Stork.initialize(new DefaultStorkInfrastructure());

        // Define a static service for the test pointing at the in-process gRPC server
        Map<String, String> params = Map.of("address-list", "127.0.0.1:" + testGrpcPort);
        var discoveryConfig = new SimpleServiceConfig.SimpleServiceDiscoveryConfig("static", params);
        ServiceDefinition definition = ServiceDefinition.of(discoveryConfig);
        Stork.getInstance().defineIfAbsent(TEST_SERVICE_NAME, definition);
    }

    @Override
    protected DynamicGrpcClientFactory getFactory() {
        return factory;
    }
}
