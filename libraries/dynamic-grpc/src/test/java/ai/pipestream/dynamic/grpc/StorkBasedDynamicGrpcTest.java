package ai.pipestream.dynamic.grpc;

import ai.pipestream.dynamic.grpc.client.DynamicGrpcClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.stork.Stork;
import io.smallrye.stork.integration.DefaultStorkInfrastructure;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class StorkBasedDynamicGrpcTest {

    private static final Logger LOG = LoggerFactory.getLogger(StorkBasedDynamicGrpcTest.class);

    @Inject
    DynamicGrpcClientFactory factory;

    @BeforeEach
    void setUp() {
        if (Stork.getInstance() == null) {
            Stork.initialize(new DefaultStorkInfrastructure());
        }
        Stork.shutdown();
        Stork.initialize(new DefaultStorkInfrastructure());
    }

    @Test
    void testServiceDefinitionCreation() {
        String serviceName = "test-service";
        assertThat(Stork.getInstance().getServiceOptional(serviceName)).isEmpty();

        try {
            factory.getMutinyClientForService(serviceName)
                .subscribe().with(
                    stub -> LOG.info("Unexpected success - stub created: {}", stub),
                    throwable -> LOG.debug("Expected failure when trying to create channel: {}", throwable.getMessage())
                );
        } catch (Exception e) {
            LOG.debug("Expected exception during service setup: {}", e.getMessage());
        }

        assertThat(Stork.getInstance().getServiceOptional(serviceName)).isPresent();
        assertThat(factory.getActiveServiceCount()).isGreaterThan(0);
    }

    @Test
    void testMultipleServiceDefinitions() {
        String service1 = "service-1";
        String service2 = "service-2";

        try {
            factory.getMutinyClientForService(service1).subscribe().with(
                stub -> LOG.debug("Service 1 stub created"),
                throwable -> LOG.debug("Service 1 failed as expected: {}", throwable.getMessage())
            );

            factory.getMutinyClientForService(service2).subscribe().with(
                stub -> LOG.debug("Service 2 stub created"),
                throwable -> LOG.debug("Service 2 failed as expected: {}", throwable.getMessage())
            );
        } catch (Exception e) {
            LOG.debug("Expected exception: {}", e.getMessage());
        }

        assertThat(Stork.getInstance().getServiceOptional(service1)).isPresent();
        assertThat(Stork.getInstance().getServiceOptional(service2)).isPresent();
        assertThat(factory.getActiveServiceCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void testServiceDefinitionIdempotency() {
        String serviceName = "idempotent-service";

        try {
            factory.getMutinyClientForService(serviceName).subscribe().with(
                stub -> LOG.debug("First call succeeded"),
                throwable -> LOG.debug("First call failed as expected")
            );

            factory.getMutinyClientForService(serviceName).subscribe().with(
                stub -> LOG.debug("Second call succeeded"),
                throwable -> LOG.debug("Second call failed as expected")
            );
        } catch (Exception e) {
            LOG.debug("Expected exception: {}", e.getMessage());
        }

        assertThat(Stork.getInstance().getServiceOptional(serviceName)).isPresent();

        long serviceCount = Stork.getInstance().getServices().values().stream()
            .filter(service -> service.getServiceName().equals(serviceName))
            .count();
        assertThat(serviceCount).isEqualTo(1);
    }
}