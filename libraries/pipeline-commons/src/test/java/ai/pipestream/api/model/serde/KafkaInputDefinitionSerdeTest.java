package ai.pipestream.api.model.serde;

import com.google.protobuf.util.JsonFormat;
import ai.pipestream.config.v1.MessagingConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaInputDefinitionSerdeTest {

    @Test
    void testMessagingConfigSerde() throws Exception {
        // MessagingConfig replaces the old KafkaInputDefinition abstraction
        MessagingConfig original = MessagingConfig.newBuilder()
                .addSubscriptions("topic-a")
                .addSubscriptions("topic-b")
                .putProperties("group.id", "my-consumer-group")
                .putProperties("max.poll.records", "500")
                .build();

        String json = JsonFormat.printer().includingDefaultValueFields().print(original);
        assertTrue(json.contains("subscriptions"));
        assertTrue(json.contains("properties"));

        MessagingConfig.Builder b = MessagingConfig.newBuilder();
        JsonFormat.parser().merge(json, b);
        MessagingConfig parsed = b.build();

        assertEquals(original, parsed);
    }
}
