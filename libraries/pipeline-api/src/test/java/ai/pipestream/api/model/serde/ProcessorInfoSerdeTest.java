package ai.pipestream.api.model.serde;

import com.google.protobuf.util.JsonFormat;
import ai.pipestream.config.v1.GrpcConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessorInfoSerdeTest {

    @Test
    void testGrpcConfigJsonRoundTrip() throws Exception {
        // Build a gRPC config akin to old ProcessorInfo(grpcServiceName)
        GrpcConfig original = GrpcConfig.newBuilder()
                .setServiceName("my-internal-bean")
                .putProperties("timeout", "5000")
                .build();

        String json = JsonFormat.printer().includingDefaultValueFields().print(original);
        assertTrue(json.contains("my-internal-bean"));

        GrpcConfig.Builder b = GrpcConfig.newBuilder();
        JsonFormat.parser().merge(json, b);
        GrpcConfig parsed = b.build();

        assertEquals(original, parsed);
    }
}
