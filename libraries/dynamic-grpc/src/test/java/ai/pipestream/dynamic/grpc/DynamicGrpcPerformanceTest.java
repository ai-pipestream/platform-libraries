package ai.pipestream.dynamic.grpc;

import ai.pipestream.dynamic.grpc.client.DynamicGrpcClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.stork.Stork;
import io.smallrye.stork.api.ServiceDefinition;
import io.smallrye.stork.integration.DefaultStorkInfrastructure;
import io.smallrye.stork.spi.config.SimpleServiceConfig;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class DynamicGrpcPerformanceTest {

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
    @DisplayName("Channel caching should improve performance")
    void testChannelCachingPerformance() {
        String serviceName = "perf-service";
        Map<String, String> params = Map.of("address-list", "localhost:9090");
        var discoveryConfig = new SimpleServiceConfig.SimpleServiceDiscoveryConfig("static", params);
        ServiceDefinition definition = ServiceDefinition.of(discoveryConfig);
        Stork.getInstance().defineIfAbsent(serviceName, definition);

        long start = System.nanoTime();
        clientFactory.getMutinyClientForService(serviceName)
            .await().atMost(Duration.ofSeconds(5));
        long firstCallTime = System.nanoTime() - start;

        List<Long> cachedCallTimes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            start = System.nanoTime();
            clientFactory.getMutinyClientForService(serviceName)
                .await().atMost(Duration.ofSeconds(1));
            cachedCallTimes.add(System.nanoTime() - start);
        }

        double avgCachedTimeMs = cachedCallTimes.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0) / 1_000_000.0;

        double firstCallTimeMs = firstCallTime / 1_000_000.0;

        System.out.printf("First call: %.2fms, Avg cached: %.2fms%n",
            firstCallTimeMs, avgCachedTimeMs);

        assertThat(avgCachedTimeMs).isLessThanOrEqualTo(firstCallTimeMs);
        assertThat(avgCachedTimeMs).isLessThan(10.0);
    }
}
