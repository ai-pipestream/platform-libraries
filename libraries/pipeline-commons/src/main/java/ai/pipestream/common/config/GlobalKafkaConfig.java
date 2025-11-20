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
 * This bean provides the standard configuration for all Pipestream microservices.
 * It enforces:
 * 1. RAW UUID Keys (16 bytes) for efficiency and strict typing.
 * 2. Apicurio/Protobuf serialization for values.
 * 3. Idempotency and high-reliability settings.
 * 4. HIGH THROUGHPUT batching for pipeline performance.
 */
@ApplicationScoped
public class GlobalKafkaConfig {

    @Produces
    @Identifier("pipestream-producer")
    public Map<String, Object> getProducerConfig() {
        Map<String, Object> props = new HashMap<>();

        // --- KEY SERIALIZATION (Strict UUID) ---
        // Using raw UUIDSerializer saves ~20 bytes per message vs String
        props.put("key.serializer", UUIDSerializer.class.getName());

        // --- VALUE SERIALIZATION (Protobuf via Apicurio) ---
        props.put("value.serializer", "io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer");

        // --- APICURIO REGISTRY CONFIG ---
        // Reads from env var, defaults to local dev registry
        String registryUrl = System.getProperty("APICURIO_REGISTRY_URL", "http://localhost:8081/apis/registry/v3");
        props.put("apicurio.registry.url", registryUrl);
        props.put("apicurio.registry.auto-register", "true");

        // --- PERFORMANCE: THROUGHPUT OPTIMIZATION ---
        // Critical for Indexing/Embeddings pipelines

        // linger.ms: Wait up to 20ms to group messages into a single batch request.
        // This drastically reduces network IOPS at the cost of trivial latency.
        props.put("linger.ms", 20);

        // batch.size: Increase to 64KB (default 16KB).
        // Embeddings/Chunks are large; this prevents splitting batches too early.
        props.put("batch.size", 65536);

        // compression.type: Snappy is fast (low CPU) and decent compression.
        // LZ4 is also a great option for pure speed if Snappy isn't available.
        props.put("compression.type", "snappy");

        // --- RELIABILITY ---
        // Idempotence guarantees exactly-once delivery order per partition
        props.put("enable.idempotence", "true");
        props.put("acks", "all");

        return props;
    }
}