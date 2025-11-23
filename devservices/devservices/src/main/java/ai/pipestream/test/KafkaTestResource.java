package ai.pipestream.test;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared test resource for configuring Kafka and Apicurio Registry in
 * integration tests.
 * This resource enforces standard ports for Docker Compose services:
 * - Kafka: localhost:9095
 * - Apicurio: localhost:8082
 *
 * Usage:
 * 
 * @QuarkusTest
 * @QuarkusTestResource(KafkaTestResource.class)
 *                                               public class MyTest { ... }
 */
public class KafkaTestResource implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        Map<String, String> config = new HashMap<>();

        // 1. Disable default DevServices to prevent conflicts with Docker Compose
        config.put("quarkus.kafka.devservices.enabled", "false");

        // 2. Point to standard Compose service ports
        config.put("kafka.bootstrap.servers", "localhost:9095");
        config.put("mp.messaging.connector.smallrye-kafka.apicurio.registry.url",
                "http://localhost:8082/apis/registry/v3");

        return config;
    }

    @Override
    public void stop() {
        // No-op: Services are managed by Docker Compose
    }
}
