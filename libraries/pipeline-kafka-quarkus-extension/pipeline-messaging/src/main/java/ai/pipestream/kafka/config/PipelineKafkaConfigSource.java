package ai.pipestream.kafka.config;

import org.eclipse.microprofile.config.spi.ConfigSource;
import java.util.*;

public class PipelineKafkaConfigSource implements ConfigSource {

    private static final String PIPELINE_TOPIC_PREFIX = "PIPELINE_TOPIC_";
    private final Map<String, String> config = new HashMap<>();

    public PipelineKafkaConfigSource() {
        this(System.getenv());
    }

    // Visible for testing
    public PipelineKafkaConfigSource(Map<String, String> env) {
        // 1. Global Defaults (UUIDs, Protobuf, Apicurio v3)
        config.put("mp.messaging.connector.smallrye-kafka.key.serializer",
                "org.apache.kafka.common.serialization.UUIDSerializer");
        config.put("mp.messaging.connector.smallrye-kafka.value.serializer",
                "io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer");
        config.put("mp.messaging.connector.smallrye-kafka.key.deserializer",
                "org.apache.kafka.common.serialization.UUIDDeserializer");
        config.put("mp.messaging.connector.smallrye-kafka.value.deserializer",
                "io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer");

        // PRODUCER PERFORMANCE & RELIABILITY
        config.put("mp.messaging.connector.smallrye-kafka.linger.ms", "20");
        config.put("mp.messaging.connector.smallrye-kafka.batch.size", "65536");
        config.put("mp.messaging.connector.smallrye-kafka.compression.type", "snappy");
        config.put("mp.messaging.connector.smallrye-kafka.enable.idempotence", "true");
        config.put("mp.messaging.connector.smallrye-kafka.acks", "all");
        config.put("mp.messaging.connector.smallrye-kafka.max.request.size", "8388608"); // 8MB

        // CONSUMER RELIABILITY
        config.put("mp.messaging.connector.smallrye-kafka.auto.offset.reset", "earliest");
        config.put("mp.messaging.connector.smallrye-kafka.enable.auto.commit", "false");

        // APICURIO CONFIGURATION
        config.put("mp.messaging.connector.smallrye-kafka.apicurio.registry.artifact-resolver-strategy",
                "io.apicurio.registry.serde.strategy.TopicIdStrategy");
        config.put("mp.messaging.connector.smallrye-kafka.apicurio.registry.auto-register", "true");
        config.put("mp.messaging.connector.smallrye-kafka.apicurio.registry.artifact-type", "PROTOBUF");
        config.put("mp.messaging.connector.smallrye-kafka.apicurio.registry.serde.find-latest", "true");

        // APICURIO REGISTRY URL (Env Var -> Sys Prop -> Config)
        String registryUrl = System.getenv("APICURIO_REGISTRY_URL");
        if (registryUrl == null || registryUrl.isBlank()) {
            registryUrl = System.getProperty("APICURIO_REGISTRY_URL");
        }
        if (registryUrl != null && !registryUrl.isBlank()) {
            config.put("mp.messaging.connector.smallrye-kafka.apicurio.registry.url", registryUrl);
        }

        // 2. Dynamic Topic Mapping from Env Vars AND System Properties (for testing)
        // Example: PIPELINE_TOPIC_INGEST_FILE ->
        // mp.messaging.outgoing.ingest-file.topic
        Map<String, String> allProps = new HashMap<>(env);
        System.getProperties().forEach((k, v) -> allProps.put(k.toString(), v.toString()));

        allProps.forEach((key, value) -> {
            if (key.startsWith(PIPELINE_TOPIC_PREFIX)) {
                String channel = key.substring(PIPELINE_TOPIC_PREFIX.length()).toLowerCase().replace("_", "-");
                String direction;

                // Explicit direction in env var (e.g. PIPELINE_TOPIC_IN_MY_CHANNEL)
                if (key.contains("_IN_")) {
                    direction = "incoming";
                    // Remove 'in-' prefix from channel name if present due to replace above
                    channel = channel.replace("in-", "");
                } else if (key.contains("_OUT_")) {
                    direction = "outgoing";
                    channel = channel.replace("out-", "");
                } else {
                    // Fallback Heuristic: If channel has 'in' or 'consumer', it's incoming.
                    direction = (channel.contains("in") || channel.contains("consumer")) ? "incoming" : "outgoing";
                }

                config.put("mp.messaging." + direction + "." + channel + ".topic", value);
                config.put("mp.messaging." + direction + "." + channel + ".connector", "smallrye-kafka");
            }
        });
    }

    @Override
    public Map<String, String> getProperties() {
        return config;
    }

    @Override
    public Set<String> getPropertyNames() {
        return config.keySet();
    }

    @Override
    public String getValue(String propertyName) {
        return config.get(propertyName);
    }

    @Override
    public String getName() {
        return "PipelineZeroConfig";
    }

    @Override
    public int getOrdinal() {
        return 300;
    } // High priority
}
