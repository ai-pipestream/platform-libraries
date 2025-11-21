package ai.pipestream.common.config;

import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.apache.kafka.common.serialization.UUIDSerializer;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized Kafka Producer Configuration.
 * <p>
 * Enforces:
 * 1. UUID Keys / Protobuf Values.
 * 2. High Throughput Batching.
 * 3. "TopicIdStrategy" - Automates Schema ID generation based on Topic Name.
 */
@ApplicationScoped
public class GlobalKafkaConfig {

    @Produces
    @Identifier("pipestream-producer")
    public Map<String, Object> getProducerConfig() {
        Map<String, Object> props = new HashMap<>();

        // --- KEY & VALUE SERIALIZERS ---
        props.put("key.serializer", UUIDSerializer.class.getName());
        props.put("value.serializer", "io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer");

        // --- APICURIO REGISTRY CONFIG ---
        String registryUrl = System.getProperty("APICURIO_REGISTRY_URL", "http://localhost:8081/apis/registry/v3");
        props.put("apicurio.registry.url", registryUrl);
        props.put("apicurio.registry.auto-register", "true");

        // --- AUTOMATION STRATEGY (The Simplifier) ---
        // This tells Apicurio: "Use the Topic Name to determine the Artifact ID"
        // Example: Topic "account-events" -> Artifact "account-events-value"
        // This removes the need to define 'artifact-id' in application.properties
        props.put("apicurio.registry.artifact-resolver-strategy",
                "io.apicurio.registry.serde.strategy.TopicIdStrategy");

        // --- PERFORMANCE TUNING (Pipeline Speed) ---
        props.put("linger.ms", 20);
        props.put("batch.size", 65536);
        props.put("compression.type", "snappy");
        props.put("enable.idempotence", "true");
        props.put("acks", "all");

        return props;
    }
}