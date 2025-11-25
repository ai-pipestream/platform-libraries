package ai.pipestream.kafka.admin;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Quarkus startup component that auto-provisions Kafka topics used by the application.
 *
 * <p>How it works:</p>
 * <ul>
 *   <li>Builds an AdminClient configuration by merging properties from two sources:
 *     <ul>
 *       <li>All properties under the {@code kafka.*} prefix are copied as-is but with the prefix removed
 *       (e.g., {@code kafka.bootstrap.servers} becomes {@code bootstrap.servers}).</li>
 *       <li>All properties under {@code mp.messaging.connector.smallrye-kafka.*} are then applied on top to allow
 *       SmallRye Kafka defaults or overrides supplied by PipelineKafkaConfigSource.</li>
 *     </ul>
 *   </li>
 *   <li>Discovers target topic names from {@code mp.messaging.*.topic} configuration entries.</li>
 *   <li>Connects to Kafka using {@link AdminClient}, checks which topics are missing, and creates them with defaults
 *       for partitions and replication factor when necessary.</li>
 * </ul>
 *
 * <p>Failure handling:</p>
 * <ul>
 *   <li>If Kafka is not configured (no {@code bootstrap.servers}), this component is a no-op.</li>
 *   <li>If topics are discovered but any step of provisioning fails during startup, a
 *   {@link TopicProvisioningException} is thrown to fail fast. This avoids running the application in a partially
 *   configured state.</li>
 * </ul>
 */
@ApplicationScoped
public class TopicProvisioner {

    private static final Logger LOG = Logger.getLogger(TopicProvisioner.class);

    @Inject
    Config config;

    /**
     * Observes the Quarkus startup event and ensures that all configured messaging topics exist in Kafka.
     *
     * <p>Topics are discovered from properties that match {@code mp.messaging.*.topic}. When at least one topic is
     * configured and Kafka is reachable, the method will create any missing topics using default values for
     * partitions and replication factor:</p>
     * <ul>
     *   <li>{@code pipeline.kafka.default-partitions} (default: 3)</li>
     *   <li>{@code pipeline.kafka.default-replication-factor} (default: 1)</li>
     * </ul>
     *
     * @param ev the Quarkus startup event
     * @throws TopicProvisioningException if Kafka is configured and topic provisioning fails for any reason
     */
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

        // Collect all 'mp.messaging.connector.smallrye-kafka.' properties (overrides kafka.*)
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
            LOG.debug("Kafka bootstrap.servers not configured; topic provisioning skipped.");
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
            LOG.debug("No mp.messaging.*.topic entries discovered; nothing to provision.");
            return;
        }

        LOG.infof("Discovered %d Kafka topic(s) from configuration.", topicsToCreate.size());

        // 3. Check Kafka and Create Topics
        try (AdminClient client = AdminClient.create(adminConfig)) {
            Set<String> existing = client.listTopics().names().get();
            topicsToCreate.removeAll(existing);

            if (!topicsToCreate.isEmpty()) {
                int partitions = config.getOptionalValue("pipeline.kafka.default-partitions", Integer.class).orElse(3);
                short replication = config
                        .getOptionalValue("pipeline.kafka.default-replication-factor", Short.class)
                        .orElse((short) 1);

                List<NewTopic> newTopics = topicsToCreate.stream()
                        .map(name -> {
                            NewTopic topic = new NewTopic(name, partitions, replication);
                            Map<String, String> topicConfigs = new HashMap<>();
                            // Keep this conservative and non-sensitive; do not log secrets.
                            topicConfigs.put("max.message.bytes", "8388608"); // 8MB
                            topic.configs(topicConfigs);
                            return topic;
                        })
                        .collect(Collectors.toList());

                LOG.infof("Creating %d Kafka topic(s): %s", newTopics.size(), topicsToCreate);
                client.createTopics(newTopics).all().get();
                LOG.infof("Successfully provisioned Kafka topic(s): %s", topicsToCreate);
            } else {
                LOG.info("All configured Kafka topics already exist; no provisioning required.");
            }
        } catch (Exception e) {
            // Fail fast when Kafka is configured and topics were expected to exist
            String message = "Failed to auto-provision Kafka topics at startup";
            LOG.error(message, e);
            throw new TopicProvisioningException(message, e);
        }
    }

    /**
     * Attempts to convert a configuration property to its appropriate type.
     * Kafka AdminClient configurations may require Integer, Long, or Boolean types
     * rather than Strings. This method tries to parse the value to the most
     * appropriate type, falling back to String if no conversion applies.
     *
     * <p>Supported conversions, in order:</p>
     * <ol>
     *   <li>Boolean (case-insensitive)</li>
     *   <li>Integer</li>
     *   <li>Long</li>
     *   <li>Double</li>
     *   <li>String (fallback)</li>
     * </ol>
     *
     * @param prop the fully-qualified config property name to read from {@link Config}
     * @return the value converted to a suitable type for Kafka client configuration
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
