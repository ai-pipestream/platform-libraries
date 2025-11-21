package ai.pipestream.common.config;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.common.annotation.Identifier;
import jakarta.inject.Inject;
import org.apache.kafka.common.serialization.UUIDSerializer;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class GlobalKafkaConfigTest {

    @Inject
    @Identifier("pipestream-producer")
    Map<String, Object> producerConfig;

    @Test
    public void testProducerConfigurationIntegrity() {
        assertNotNull(producerConfig, "Producer config map should be injectable");

        // 1. Verify Strict Typing (UUID)
        assertEquals(UUIDSerializer.class.getName(), producerConfig.get("key.serializer"), 
            "Must use UUIDSerializer for keys");

        // 2. Verify Apicurio Strategy (The Automation)
        assertEquals("io.apicurio.registry.serde.strategy.TopicIdStrategy", 
            producerConfig.get("apicurio.registry.artifact-resolver-strategy"),
            "Must use TopicIdStrategy to automate artifact IDs");

        // 3. Verify High Throughput Settings
        assertEquals(20, producerConfig.get("linger.ms"), "Must have linger.ms enabled for batching");
        assertEquals(65536, producerConfig.get("batch.size"), "Must have increased batch size");
        assertEquals("all", producerConfig.get("acks"), "Must require all acks for data safety");
    }
}