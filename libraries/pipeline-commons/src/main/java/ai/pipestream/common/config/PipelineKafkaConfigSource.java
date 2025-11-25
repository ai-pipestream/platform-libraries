package ai.pipestream.common.config;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Global Kafka Configuration Source.
 * <p>
 * This class automatically applies standard Pipeline Kafka configurations (UUID
 * keys, Protobuf values)
 * to ALL outgoing and incoming Kafka channels by setting defaults on the
 * 'smallrye-kafka' connector.
 * <p>
 * It uses MicroProfile Config to inject these defaults at a low priority (250),
 * allowing
 * application.properties (260+) to override them if necessary.
 */
public class PipelineKafkaConfigSource implements ConfigSource {

    private static final Logger LOG = Logger.getLogger(PipelineKafkaConfigSource.class);

    private static final Map<String, String> CONFIG = new HashMap<>();

    static {
        // --- GLOBAL CONNECTOR DEFAULTS ---
        // Applying properties to the connector itself serves as a default for all
        // channels using this connector.
        // This avoids using "mp.messaging.outgoing.*" wildcards which can cause
        // SmallRye to crash
        // by incorrectly identifying "*" as a channel name.

        // SERIALIZATION (Standard Pipeline Format)
        CONFIG.put("mp.messaging.connector.smallrye-kafka.key.serializer",
                "org.apache.kafka.common.serialization.UUIDSerializer");
        CONFIG.put("mp.messaging.connector.smallrye-kafka.value.serializer",
                "io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer");
        CONFIG.put("mp.messaging.connector.smallrye-kafka.key.deserializer",
                "org.apache.kafka.common.serialization.UUIDDeserializer");
        CONFIG.put("mp.messaging.connector.smallrye-kafka.value.deserializer",
                "io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer");

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
        // Auto-register schemas by default (useful for dev/test)
        CONFIG.put("mp.messaging.connector.smallrye-kafka.apicurio.registry.auto-register", "true");

        // Explicitly set artifact type (though ProtobufSerializer implies it, this is
        // safer)
        CONFIG.put("mp.messaging.connector.smallrye-kafka.apicurio.registry.artifact-type", "PROTOBUF");

        // Find latest schema version if not specified (critical for consumers in
        // dynamic environments)
        CONFIG.put("mp.messaging.connector.smallrye-kafka.apicurio.registry.serde.find-latest", "true");

        // --- APICURIO REGISTRY URL ---
        // Prefer an explicit environment variable, then a system property.
        // Only set this value if it is actually provided. Leaving it unset
        // allows dev/test infrastructure (e.g., Quarkus DevServices / Compose)
        // or application.properties to supply the correct URL dynamically.
        String registryUrl = System.getenv("APICURIO_REGISTRY_URL");
        if (registryUrl == null || registryUrl.isBlank()) {
            registryUrl = System.getProperty("APICURIO_REGISTRY_URL");
        }
        if (registryUrl != null && !registryUrl.isBlank()) {
            CONFIG.put("mp.messaging.connector.smallrye-kafka.apicurio.registry.url", registryUrl);
        }

        // Log all properties that are being set at startup for visibility
        // This helps diagnose effective defaults applied to the SmallRye Kafka connector
        if (LOG.isInfoEnabled()) {
            LOG.info("PipelineKafkaConfigSource initializing default Kafka properties");
            for (Map.Entry<String, String> entry : CONFIG.entrySet()) {
                LOG.infof("  %s = %s", entry.getKey(), entry.getValue());
            }
        }
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