package ai.pipestream.dynamic.grpc;

import ai.pipestream.dynamic.grpc.client.DynamicGrpcClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.smallrye.stork.Stork;
import io.smallrye.stork.api.ServiceDefinition;
import io.smallrye.stork.integration.DefaultStorkInfrastructure;
import io.smallrye.stork.spi.config.SimpleServiceConfig;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
public class DynamicGrpcEdgeCasesTest {

    @Inject
    DynamicGrpcClientFactory clientFactory;

    @BeforeEach
    void setup() {
        if (Stork.getInstance() != null) {
            Stork.shutdown();
        }
        Stork.initialize(new DefaultStorkInfrastructure());
    }

    @Test
    @DisplayName("Empty service list should throw proper exception")
    void testEmptyServiceList() {
        assertThatThrownBy(() ->
            clientFactory.getMutinyClientForService("non-existent-service")
                .await().atMost(Duration.ofSeconds(2))
        ).isInstanceOf(io.grpc.StatusRuntimeException.class).hasMessageContaining("UNAVAILABLE: No instances found for service");
    }

    @Test
    @DisplayName("Handle invalid service names")
    void testInvalidServiceNames() {
        assertThatThrownBy(() ->
            clientFactory.getMutinyClientForService(null)
                .await().atMost(Duration.ofSeconds(1))
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
            clientFactory.getMutinyClientForService("")
                .await().atMost(Duration.ofSeconds(1))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Concurrent service discovery should not create duplicate channels")
    void testConcurrentServiceDiscovery() throws InterruptedException {
        Map<String, String> params = Map.of("address-list", "localhost:9090");
        var discoveryConfig = new SimpleServiceConfig.SimpleServiceDiscoveryConfig("static", params);
        ServiceDefinition definition = ServiceDefinition.of(discoveryConfig);
        Stork.getInstance().defineIfAbsent("concurrent-service", definition);

        int initialChannelCount = clientFactory.getActiveServiceCount();

        int concurrentRequests = 10;
        AtomicInteger completedRequests = new AtomicInteger(0);

        for (int i = 0; i < concurrentRequests; i++) {
            Uni.createFrom().item(i)
                .onItem().transformToUni(idx ->
                    clientFactory.getMutinyClientForService("concurrent-service")
                )
                .subscribe().with(
                    client -> completedRequests.incrementAndGet(),
                    error -> completedRequests.incrementAndGet()
                );
        }

        Thread.sleep(1000);

        assertThat(clientFactory.getActiveServiceCount())
            .isEqualTo(initialChannelCount + 1);
    }
}
