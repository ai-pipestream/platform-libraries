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
                adminConfig.put(prop.substring(6), config.getValue(prop, String.class));
            }
        }

        // Collect all 'mp.messaging.connector.smallrye-kafka.' properties (overrides
        // kafka.*)
        // This is where PipelineKafkaConfigSource puts its defaults
        String smallryePrefix = "mp.messaging.connector.smallrye-kafka.";
        for (String prop : config.getPropertyNames()) {
            if (prop.startsWith(smallryePrefix)) {
                adminConfig.put(prop.substring(smallryePrefix.length()), config.getValue(prop, String.class));
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
}
