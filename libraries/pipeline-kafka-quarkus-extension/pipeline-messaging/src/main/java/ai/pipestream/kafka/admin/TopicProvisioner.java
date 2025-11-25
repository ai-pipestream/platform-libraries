package ai.pipestream.kafka.admin;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class TopicProvisioner {

    @Inject
    Config config;

    void onStart(@Observes StartupEvent ev) {
        // 1. Build Admin Client Configuration
        Map<String, Object> adminConfig = new HashMap<>();

        // Collect all 'kafka.' properties (e.g. kafka.bootstrap.servers ->
        // bootstrap.servers)
        for (String prop : config.getPropertyNames()) {
            if (prop.startsWith("kafka.")) {
                String key = prop.substring(6);
                adminConfig.put(key, getTypedConfigValue(prop));
            }
        }

        // Collect all 'mp.messaging.connector.smallrye-kafka.' properties (overrides
        // kafka.*)
        // This is where PipelineKafkaConfigSource puts its defaults
        String smallryePrefix = "mp.messaging.connector.smallrye-kafka.";
        for (String prop : config.getPropertyNames()) {
            if (prop.startsWith(smallryePrefix)) {
                String key = prop.substring(smallryePrefix.length());
                adminConfig.put(key, getTypedConfigValue(prop));
            }
        }

        // Check if we have bootstrap servers
        if (!adminConfig.containsKey("bootstrap.servers")) {
            return; // No Kafka configured
        }

        // 2. Identify all topics we intend to use
        Set<String> topicsToCreate = new HashSet<>();
        for (String prop : config.getPropertyNames()) {
            if (prop.contains(".topic") && prop.startsWith("mp.messaging")) {
                topicsToCreate.add(config.getValue(prop, String.class));
            }
        }

        if (topicsToCreate.isEmpty()) {
            return;
        }

        // 3. Check Kafka and Create Topics
        try (AdminClient client = AdminClient.create(adminConfig)) {
            Set<String> existing = client.listTopics().names().get();
            topicsToCreate.removeAll(existing);

            if (!topicsToCreate.isEmpty()) {
                int partitions = config.getOptionalValue("pipeline.kafka.default-partitions", Integer.class).orElse(3);
                short replication = config.getOptionalValue("pipeline.kafka.default-replication-factor", Short.class)
                        .orElse((short) 1);

                List<NewTopic> newTopics = topicsToCreate.stream()
                        .map(name -> {
                            NewTopic topic = new NewTopic(name, partitions, replication);
                            Map<String, String> topicConfigs = new HashMap<>();
                            topicConfigs.put("max.message.bytes", "8388608"); // 8MB
                            topic.configs(topicConfigs);
                            return topic;
                        })
                        .collect(Collectors.toList());

                client.createTopics(newTopics).all().get();
                System.out.println("Pipeline: Auto-provisioned topics: " + topicsToCreate);
            }
        } catch (Exception e) {
            // Log warning but don't crash (Kafka might be down temporarily)
            System.err.println("Warning: Could not auto-provision topics: " + e.getMessage());
        }
    }

    /**
     * Attempts to convert a configuration property to its appropriate type.
     * Kafka AdminClient configurations may require Integer, Long, or Boolean types
     * rather than Strings. This method tries to parse the value to the most
     * appropriate type, falling back to String if no conversion applies.
     */
    private Object getTypedConfigValue(String prop) {
        String stringValue = config.getValue(prop, String.class);
        
        // Try Boolean
        if ("true".equalsIgnoreCase(stringValue) || "false".equalsIgnoreCase(stringValue)) {
            return Boolean.parseBoolean(stringValue);
        }
        
        // Try numeric types - Integer first, then Long for larger values
        try {
            return Integer.parseInt(stringValue);
        } catch (NumberFormatException ignored) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException ignored1) {
                // Try Double for floating point values (handles scientific notation too)
                try {
                    return Double.parseDouble(stringValue);
                } catch (NumberFormatException ignored2) {
                    // Not a numeric type
                }
            }
        }
        
        // Return as String (Kafka's AbstractConfig can handle string conversion)
        return stringValue;
    }
}
