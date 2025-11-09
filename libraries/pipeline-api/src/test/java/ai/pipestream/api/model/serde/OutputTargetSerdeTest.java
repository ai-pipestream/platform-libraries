package ai.pipestream.api.model.serde;

import com.google.protobuf.util.JsonFormat;
import ai.pipestream.config.v1.GrpcConfig;
import ai.pipestream.config.v1.MessagingConfig;
import ai.pipestream.config.v1.TransportConfig;
import ai.pipestream.config.v1.TransportType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputTargetSerdeTest {

    @Test
    void testMessagingTransportSerde() throws Exception {
        MessagingConfig messaging = MessagingConfig.newBuilder()
                .addTopics("my-output-topic")
                .putProperties("acks", "all")
                .build();

        TransportConfig original = TransportConfig.newBuilder()
                .setType(TransportType.TRANSPORT_TYPE_MESSAGING)
                .setMessaging(messaging)
                .setMaxRetries(5)
                .build();

        String json = JsonFormat.printer().includingDefaultValueFields().print(original);
        assertTrue(json.contains("\"type\":"));
        assertTrue(json.contains("\"messaging\""));

        TransportConfig.Builder b = TransportConfig.newBuilder();
        JsonFormat.parser().merge(json, b);
        TransportConfig parsed = b.build();

        assertEquals(original, parsed);
    }

    @Test
    void testGrpcTransportSerde() throws Exception {
        GrpcConfig grpc = GrpcConfig.newBuilder()
                .setServiceName("my-grpc-service")
                .putProperties("timeout", "5000")
                .build();

        TransportConfig original = TransportConfig.newBuilder()
                .setType(TransportType.TRANSPORT_TYPE_GRPC)
                .setGrpc(grpc)
                .build();

        String json = JsonFormat.printer().includingDefaultValueFields().print(original);
        assertTrue(json.contains("\"grpc\""));

        TransportConfig.Builder b = TransportConfig.newBuilder();
        JsonFormat.parser().merge(json, b);
        TransportConfig parsed = b.build();

        assertEquals(original, parsed);
    }
}