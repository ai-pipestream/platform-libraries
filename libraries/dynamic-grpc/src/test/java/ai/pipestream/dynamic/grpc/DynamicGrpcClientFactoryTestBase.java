package ai.pipestream.dynamic.grpc;

import ai.pipestream.data.module.ModuleProcessRequest;
import ai.pipestream.data.module.ModuleProcessResponse;
import ai.pipestream.data.module.MutinyPipeStepProcessorGrpc;
import ai.pipestream.data.module.ServiceMetadata;
import ai.pipestream.data.util.proto.PipeDocTestDataFactory;
import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.dynamic.grpc.client.DynamicGrpcClientFactory;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class DynamicGrpcClientFactoryTestBase {

    protected static Server testGrpcServer;
    protected static int testGrpcPort;
    private static final PipeDocTestDataFactory testDataFactory = new PipeDocTestDataFactory();
    protected static final String TEST_SERVICE_NAME = "test-service";

    protected abstract DynamicGrpcClientFactory getFactory();

    @BeforeAll
    static void startTestServer() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            testGrpcPort = socket.getLocalPort();
        }

        testGrpcServer = ServerBuilder.forPort(testGrpcPort)
            .addService(new TestPipeStepProcessor())
            .build()
            .start();
    }

    @AfterAll
    static void stopTestServer() throws InterruptedException {
        if (testGrpcServer != null) {
            testGrpcServer.shutdown();
            testGrpcServer.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void testClientCreationAndCall() {
        var clientUni = getFactory().getMutinyClientForService(TEST_SERVICE_NAME);
        var client = clientUni.await().atMost(java.time.Duration.ofSeconds(5));
        assertThat(client).isNotNull();

        ModuleProcessRequest request = createTestRequest();
        ModuleProcessResponse response = client.processData(request)
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getOutputDoc().getSearchMetadata().getBody()).contains("Processed:");
    }

    @Test
    void testClientReuse() {
        DynamicGrpcClientFactory factory = getFactory();

        var client1Uni = factory.getMutinyClientForService(TEST_SERVICE_NAME);
        var client2Uni = factory.getMutinyClientForService(TEST_SERVICE_NAME);

        var client1 = client1Uni.await().atMost(java.time.Duration.ofSeconds(5));
        var client2 = client2Uni.await().atMost(java.time.Duration.ofSeconds(5));

        assertThat(factory.getActiveServiceCount()).isEqualTo(1);

        ModuleProcessRequest request = createTestRequest();

        ModuleProcessResponse response1 = client1.processData(request)
            .await().atMost(java.time.Duration.ofSeconds(5));
        ModuleProcessResponse response2 = client2.processData(request)
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertThat(response1.getSuccess()).isTrue();
        assertThat(response2.getSuccess()).isTrue();
    }

    protected ModuleProcessRequest createTestRequest() {
        return ModuleProcessRequest.newBuilder()
            .setDocument(testDataFactory.createComplexDocument(1))
            .setMetadata(ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName("test-step")
                .setStreamId("test-stream")
                .build())
            .build();
    }

    static class TestPipeStepProcessor extends MutinyPipeStepProcessorGrpc.PipeStepProcessorImplBase {
        @Override
        public Uni<ModuleProcessResponse> processData(ModuleProcessRequest request) {
            PipeDoc originalDoc = request.getDocument();
            PipeDoc processedDoc = originalDoc.toBuilder()
                .setSearchMetadata(originalDoc.getSearchMetadata().toBuilder()
                    .setBody("Processed: " + originalDoc.getSearchMetadata().getBody())
                    .build())
                .build();

            ModuleProcessResponse response = ModuleProcessResponse.newBuilder()
                .setSuccess(true)
                .setOutputDoc(processedDoc)
                .build();
            return Uni.createFrom().item(response);
        }
    }
}
