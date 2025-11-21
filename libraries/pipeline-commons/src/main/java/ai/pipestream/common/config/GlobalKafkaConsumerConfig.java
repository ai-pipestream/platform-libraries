package ai.pipestream.common.config;

import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.apache.kafka.common.serialization.UUIDDeserializer;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized Kafka Consumer Configuration.
 * <p>
 * Use this via: mp.messaging.incoming.[channel].kafka-configuration=pipestream-consumer
 * <p>
 * Enforces:
 * 1. UUID/Protobuf Deserialization.
 * 2. "TopicIdStrategy" - Automates Schema lookup based on Topic Name.
 * 3. Reliability (No auto-commit, earliest offset).
 */
@ApplicationScoped
public class GlobalKafkaConsumerConfig {

    @Produces
    @Identifier("pipestream-consumer")
    public Map<String, Object> getConsumerConfig() {
        Map<String, Object> props = new HashMap<>();

        // --- KEY DESERIALIZATION (Strict UUID) ---
        // Must match the Producer's UUIDSerializer
        props.put("key.deserializer", UUIDDeserializer.class.getName());

        // --- VALUE DESERIALIZATION (Protobuf) ---
        props.put("value.deserializer", "io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer");

        // --- APICURIO CONFIG ---
        String registryUrl = System.getProperty("APICURIO_REGISTRY_URL", "http://localhost:8081/apis/registry/v3");
        props.put("apicurio.registry.url", registryUrl);

        // --- AUTOMATION STRATEGY (The Simplifier) ---
        // Tells consumer to look up schema 'topic-name-value' automatically.
        // Example: Topic "account-events" -> Artifact "account-events-value"
        props.put("apicurio.registry.artifact-resolver-strategy",
                "io.apicurio.registry.serde.strategy.TopicIdStrategy");

        // --- REWIND & RELIABILITY ---
        // "earliest": If we create a new consumer group (e.g. for a full re-index),
        // start from the beginning of the topic, not the end.
        props.put("auto.offset.reset", "earliest");

        // Disable auto-commit to ensure we process the data (s3 download etc) 
        // before acknowledging. We control this in code.
        props.put("enable.auto.commit", "false");

        return props;
    }
}