package ai.pipestream.common.config;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.common.annotation.Identifier;
import jakarta.inject.Inject;
import org.apache.kafka.common.serialization.UUIDDeserializer;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class GlobalKafkaConsumerConfigTest {

    @Inject
    @Identifier("pipestream-consumer")
    Map<String, Object> consumerConfig;

    @Test
    public void testConsumerConfigurationIntegrity() {
        assertNotNull(consumerConfig, "Consumer config map should be injectable");

        // 1. Verify Strict Typing (UUID)
        assertEquals(UUIDDeserializer.class.getName(), consumerConfig.get("key.deserializer"),
            "Must use UUIDDeserializer for keys");

        // 2. Verify Apicurio Strategy (Automation)
        assertEquals("io.apicurio.registry.serde.strategy.TopicIdStrategy",
            consumerConfig.get("apicurio.registry.artifact-resolver-strategy"),
            "Must use TopicIdStrategy to find schema artifacts");

        // 3. Verify Reliability Settings
        assertEquals("false", consumerConfig.get("enable.auto.commit"),
            "Auto-commit must be disabled to support Claim-Check/DLQ patterns");
        assertEquals("earliest", consumerConfig.get("auto.offset.reset"),
            "Offset reset must be 'earliest' to support Pipeline Rewinds");
    }
}