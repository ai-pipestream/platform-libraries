package ai.pipestream.common.config;

import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Global Kafka Configuration Source.
 * <p>
 * This class automatically applies standard Pipeline Kafka configurations (UUID keys, Protobuf values)
 * to ALL outgoing and incoming Kafka channels by setting defaults on the 'smallrye-kafka' connector.
 * <p>
 * It uses MicroProfile Config to inject these defaults at a low priority (250), allowing
 * application.properties (260+) to override them if necessary.
 */
public class PipelineKafkaConfigSource implements ConfigSource {

    private static final Map<String, String> CONFIG = new HashMap<>();

    static {
        // --- GLOBAL CONNECTOR DEFAULTS ---
        // Applying properties to the connector itself serves as a default for all channels using this connector.
        // This avoids using "mp.messaging.outgoing.*" wildcards which can cause SmallRye to crash 
        // by incorrectly identifying "*" as a channel name.

        // SERIALIZATION (Standard Pipeline Format)
        CONFIG.put("mp.messaging.connector.smallrye-kafka.key.serializer", "org.apache.kafka.common.serialization.UUIDSerializer");
        CONFIG.put("mp.messaging.connector.smallrye-kafka.value.serializer", "io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer");
        CONFIG.put("mp.messaging.connector.smallrye-kafka.key.deserializer", "org.apache.kafka.common.serialization.UUIDDeserializer");
        CONFIG.put("mp.messaging.connector.smallrye-kafka.value.deserializer", "io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer");

        // PRODUCER PERFORMANCE & RELIABILITY
        // These will be passed to the Kafka Producer
        CONFIG.put("mp.messaging.connector.smallrye-kafka.linger.ms", "20");
        CONFIG.put("mp.messaging.connector.smallrye-kafka.batch.size", "65536");
        CONFIG.put("mp.messaging.connector.smallrye-kafka.compression.type", "snappy");
        CONFIG.put("mp.messaging.connector.smallrye-kafka.enable.idempotence", "true");
        CONFIG.put("mp.messaging.connector.smallrye-kafka.acks", "all");

        // CONSUMER RELIABILITY
        // These will be passed to the Kafka Consumer
        CONFIG.put("mp.messaging.connector.smallrye-kafka.auto.offset.reset", "earliest");
        CONFIG.put("mp.messaging.connector.smallrye-kafka.enable.auto.commit", "false");

        // APICURIO CONFIGURATION
        CONFIG.put("mp.messaging.connector.smallrye-kafka.apicurio.registry.artifact-resolver-strategy", 
                   "io.apicurio.registry.serde.strategy.TopicIdStrategy");
        CONFIG.put("mp.messaging.connector.smallrye-kafka.apicurio.registry.auto-register", "true");

        // APICURIO REGISTRY URL
        String registryUrl = System.getProperty("APICURIO_REGISTRY_URL");
        if (registryUrl == null || registryUrl.isEmpty()) {
            registryUrl = "http://localhost:8081/apis/registry/v3";
        }
        CONFIG.put("mp.messaging.connector.smallrye-kafka.apicurio.registry.url", registryUrl);
    }

    @Override
    public Map<String, String> getProperties() {
        return CONFIG;
    }

    @Override
    public Set<String> getPropertyNames() {
        return CONFIG.keySet();
    }

    @Override
    public String getValue(String propertyName) {
        if (CONFIG.containsKey(propertyName)) {
            return CONFIG.get(propertyName);
        }
        return null;
    }

    @Override
    public String getName() {
        return "PipelineKafkaDefaults";
    }

    @Override
    public int getOrdinal() {
        return 250;
    }
}