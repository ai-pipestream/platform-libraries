package ai.pipestream.kafka.config;

import org.eclipse.microprofile.config.spi.ConfigSource;
import java.util.*;

public class PipelineKafkaConfigSource implements ConfigSource {

    private final Map<String, String> config = new HashMap<>();

    public PipelineKafkaConfigSource() {
        this(System.getenv());
    }

    // Visible for testing
    public PipelineKafkaConfigSource(Map<String, String> env) {
        // 1. Global Defaults (UUIDs, Protobuf, Apicurio v3)
        config.put("mp.messaging.connector.smallrye-kafka.key.serializer", "org.apache.kafka.common.serialization.UUIDSerializer");
        config.put("mp.messaging.connector.smallrye-kafka.value.serializer", "io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer");
        config.put("mp.messaging.connector.smallrye-kafka.key.deserializer", "org.apache.kafka.common.serialization.UUIDDeserializer");
        config.put("mp.messaging.connector.smallrye-kafka.value.deserializer", "io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer");
        
        // 2. Dynamic Topic Mapping from Env Vars AND System Properties (for testing)
        // Example: PIPELINE_TOPIC_INGEST_FILE -> mp.messaging.outgoing.ingest-file.topic
        Map<String, String> allProps = new HashMap<>(env);
        System.getProperties().forEach((k, v) -> allProps.put(k.toString(), v.toString()));

        allProps.forEach((key, value) -> {
            if (key.startsWith("PIPELINE_TOPIC_")) {
                String channel = key.substring(15).toLowerCase().replace("_", "-");
                // Heuristic: If channel has 'in' or 'consumer', it's incoming.
                String direction = (channel.contains("in") || channel.contains("consumer")) ? "incoming" : "outgoing";
                
                config.put("mp.messaging." + direction + "." + channel + ".topic", value);
                config.put("mp.messaging." + direction + "." + channel + ".connector", "smallrye-kafka");
            }
        });
    }

    @Override
    public Map<String, String> getProperties() { return config; }
    @Override
    public Set<String> getPropertyNames() { return config.keySet(); }
    @Override
    public String getValue(String propertyName) { return config.get(propertyName); }
    @Override
    public String getName() { return "PipelineZeroConfig"; }
    @Override
    public int getOrdinal() { return 300; } // High priority
}
