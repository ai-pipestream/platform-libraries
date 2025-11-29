package ai.pipestream.dynamic.grpc;

import ai.pipestream.grpc.wiremock.client.WireMockServerTestResource;
import com.github.tomakehurst.wiremock.client.WireMock;
import ai.pipestream.data.module.ModuleProcessRequest;
import ai.pipestream.data.module.ModuleProcessResponse;
import ai.pipestream.data.module.ServiceMetadata;
import ai.pipestream.data.util.proto.PipeDocTestDataFactory;
import ai.pipestream.dynamic.grpc.client.DynamicGrpcClientFactory;
import ai.pipestream.opensearch.v1.FilesystemMetaSearchRequest;
import ai.pipestream.opensearch.v1.FilesystemMetaSearchResponse;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.stork.Stork;
import io.smallrye.stork.api.ServiceDefinition;
import io.smallrye.stork.integration.DefaultStorkInfrastructure;
import io.smallrye.stork.spi.config.SimpleServiceConfig;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static ai.pipestream.grpc.wiremock.client.WireMockGrpcClient.*;

@QuarkusTest
@QuarkusTestResource(WireMockServerTestResource.class)
class DynamicGrpcWireMockTest {

    @Inject
    DynamicGrpcClientFactory factory;

    @ConfigProperty(name = "wiremock.url")
    String wiremockUrl;

    private final PipeDocTestDataFactory testDataFactory = new PipeDocTestDataFactory();

    @BeforeEach
    void setupStork() {
        int httpPort = Integer.parseInt(wiremockUrl.substring(wiremockUrl.lastIndexOf(":") + 1));
        
        // Configure WireMock Client
        WireMock.configureFor("localhost", httpPort);
        WireMock.reset();

        // Configure Stork to point to the WireMock container
        if (Stork.getInstance() != null) {
            Stork.shutdown();
        }
        Stork.initialize(new DefaultStorkInfrastructure());
        
        // Since we multiplex gRPC on the HTTP port in the container/extension, point Stork there
        defineStaticService("echo", httpPort);
        defineStaticService("opensearch-manager", httpPort);
    }

    private void defineStaticService(String serviceName, int port) {
        Map<String, String> params = Map.of("address-list", "localhost:" + port);
        var discoveryConfig = new SimpleServiceConfig.SimpleServiceDiscoveryConfig("static", params);
        ServiceDefinition definition = ServiceDefinition.of(discoveryConfig);
        Stork.getInstance().defineIfAbsent(serviceName, definition);
    }

    @Test
    void shouldCreatePipeStepProcessorClientAndProcess() {
        // Stub ProcessData to return a success with modified body
        ModuleProcessResponse response = ModuleProcessResponse.newBuilder().setSuccess(true).build();
        
        String serviceName = "ai.pipestream.data.module.PipeStepProcessor";
        
        WireMock.stubFor(
            grpcStubFor(serviceName, "ProcessData")
                .willReturn(aGrpcResponseWith(response))
        );

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
        String serviceName = "ai.pipestream.opensearch.v1.OpenSearchManagerService";
        
        FilesystemMetaSearchResponse response = FilesystemMetaSearchResponse.newBuilder()
            .addResults(ai.pipestream.opensearch.v1.FilesystemSearchResult.newBuilder().setNodeId("n1").setName("doc").setNodeType("FILE").setScore(0.9f).build())
            .setTotalCount(1)
            .build();
            
        WireMock.stubFor(
            grpcStubFor(serviceName, "SearchFilesystemMeta")
                .willReturn(aGrpcResponseWith(response))
        );

        var stub = factory.getOpenSearchManagerClient("opensearch-manager").await().atMost(java.time.Duration.ofSeconds(5));
        var req = FilesystemMetaSearchRequest.newBuilder().setDrive("d").setQuery("q").build();
        var resp = stub.searchFilesystemMeta(req).await().atMost(java.time.Duration.ofSeconds(5));
        assertThat(resp.getTotalCount()).isEqualTo(1);
        assertThat(resp.getResultsCount()).isEqualTo(1);
    }
}