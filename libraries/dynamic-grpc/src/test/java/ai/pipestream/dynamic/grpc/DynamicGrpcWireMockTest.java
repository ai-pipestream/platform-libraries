package ai.pipestream.dynamic.grpc;

import ai.pipestream.grpc.wiremock.InjectWireMock;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import ai.pipestream.data.module.ModuleProcessRequest;
import ai.pipestream.data.module.ModuleProcessResponse;
import ai.pipestream.data.module.PipeStepProcessorGrpc;
import ai.pipestream.data.module.ServiceMetadata;
import ai.pipestream.data.util.proto.PipeDocTestDataFactory;
import ai.pipestream.dynamic.grpc.client.DynamicGrpcClientFactory;
import ai.pipestream.opensearch.v1.FilesystemMetaSearchRequest;
import ai.pipestream.opensearch.v1.FilesystemMetaSearchResponse;
import ai.pipestream.opensearch.v1.OpenSearchManagerServiceGrpc;
// Use fully-qualified name for SearchResult to avoid naming clashes
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.stork.Stork;
import io.smallrye.stork.api.ServiceDefinition;
import io.smallrye.stork.integration.DefaultStorkInfrastructure;
import io.smallrye.stork.spi.config.SimpleServiceConfig;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wiremock.grpc.dsl.WireMockGrpcService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static ai.pipestream.grpc.wiremock.WireMockGrpcCompat.method;
import static ai.pipestream.grpc.wiremock.WireMockGrpcCompat.message;

@QuarkusTest
@QuarkusTestResource(LocalWireMockGrpcTestResource.class)
class DynamicGrpcWireMockTest {

    @Inject
    DynamicGrpcClientFactory factory;

    @InjectWireMock
    WireMockServer wireMock;

    private final PipeDocTestDataFactory testDataFactory = new PipeDocTestDataFactory();

    @BeforeEach
    void setupStork() {
        if (Stork.getInstance() != null) {
            Stork.shutdown();
        }
        Stork.initialize(new DefaultStorkInfrastructure());
        int port = wireMock.port();
        defineStaticService("echo", port);
        defineStaticService("opensearch-manager", port);
    }

    private void defineStaticService(String serviceName, int port) {
        Map<String, String> params = Map.of("address-list", "127.0.0.1:" + port);
        var discoveryConfig = new SimpleServiceConfig.SimpleServiceDiscoveryConfig("static", params);
        ServiceDefinition definition = ServiceDefinition.of(discoveryConfig);
        Stork.getInstance().defineIfAbsent(serviceName, definition);
    }

    // @Test  // Temporarily disabled pending WireMock gRPC fix for this service
    void shouldCreatePipeStepProcessorClientAndProcess() {
        WireMockGrpcService svc = new WireMockGrpcService(new WireMock(wireMock.port()), PipeStepProcessorGrpc.SERVICE_NAME);
        // Stub ProcessData to return a success with modified body
        ModuleProcessResponse response = ModuleProcessResponse.newBuilder().setSuccess(true).build();
        svc.stubFor(method("ProcessData").willReturn(
            message(response)
        ));

        var stub = factory.getMutinyClientForService("echo").await().atMost(java.time.Duration.ofSeconds(5));
        var request = ModuleProcessRequest.newBuilder()
            .setDocument(ai.pipestream.data.v1.PipeDoc.getDefaultInstance())
            .setMetadata(ServiceMetadata.newBuilder().setPipelineName("wiremock").setPipeStepName("step").setStreamId("s").build())
            .build();
        var resp = stub.processData(request).await().atMost(java.time.Duration.ofSeconds(5));
        assertThat(resp).isNotNull();
        assertThat(resp.getSuccess()).isTrue();
    }

    @Test
    void shouldCreateOpenSearchClientAndSearch() {
        WireMockGrpcService svc = new WireMockGrpcService(new WireMock(wireMock.port()), OpenSearchManagerServiceGrpc.SERVICE_NAME);
        FilesystemMetaSearchResponse response = FilesystemMetaSearchResponse.newBuilder()
            .addResults(ai.pipestream.opensearch.v1.FilesystemSearchResult.newBuilder().setNodeId("n1").setName("doc").setNodeType("FILE").setScore(0.9f).build())
            .setTotalCount(1)
            .build();
        svc.stubFor(method("SearchFilesystemMeta").willReturn(message(response)));

        var stub = factory.getOpenSearchManagerClient("opensearch-manager").await().atMost(java.time.Duration.ofSeconds(5));
        var req = FilesystemMetaSearchRequest.newBuilder().setDrive("d").setQuery("q").build();
        var resp = stub.searchFilesystemMeta(req).await().atMost(java.time.Duration.ofSeconds(5));
        assertThat(resp.getTotalCount()).isEqualTo(1);
        assertThat(resp.getResultsCount()).isEqualTo(1);
    }
}
