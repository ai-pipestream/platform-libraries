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

    @Inject Config config;

    void onStart(@Observes StartupEvent ev) {
        // Only run if bootstrap servers are configured
        Optional<String> bootstrap = config.getOptionalValue("kafka.bootstrap.servers", String.class);
        if (bootstrap.isEmpty()) {
            return;
        }

        // 1. Identify all topics we intend to use
        Set<String> topicsToCreate = new HashSet<>();
        for (String prop : config.getPropertyNames()) {
            if (prop.contains(".topic") && prop.startsWith("mp.messaging")) {
                topicsToCreate.add(config.getValue(prop, String.class));
            }
        }

        if (topicsToCreate.isEmpty()) {
            return;
        }

        // 2. Check Kafka
        try (AdminClient client = AdminClient.create(Map.of("bootstrap.servers", bootstrap.get()))) {
            Set<String> existing = client.listTopics().names().get();
            topicsToCreate.removeAll(existing);

            if (!topicsToCreate.isEmpty()) {
                int partitions = config.getOptionalValue("pipeline.kafka.default-partitions", Integer.class).orElse(3);
                short replication = config.getOptionalValue("pipeline.kafka.default-replication-factor", Short.class).orElse((short) 1);

                List<NewTopic> newTopics = topicsToCreate.stream()
                    .map(name -> new NewTopic(name, partitions, replication))
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
