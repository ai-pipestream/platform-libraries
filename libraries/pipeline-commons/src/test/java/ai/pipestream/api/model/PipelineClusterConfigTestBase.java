package ai.pipestream.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import ai.pipestream.config.v1.Cluster;
import ai.pipestream.config.v1.ClusterMetadata;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Updated base test class for Cluster (gRPC) serialization/deserialization.
 * The legacy JSON/record-based PipeDoc models were removed.
 * These tests now validate the gRPC messages defined in pipeline_config_models.proto.
 */
public abstract class PipelineClusterConfigTestBase {

    // Kept for compatibility with subclasses; not used in these gRPC-based tests
    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testBuildClusterWithAllowedLists() {
        ClusterMetadata metadata = ClusterMetadata.newBuilder()
                .setName("production-cluster-meta")
                .setCreatedAt(1700000000000L)
                .setMetadata(Struct.getDefaultInstance())
                .build();

        Cluster cluster = Cluster.newBuilder()
                .setClusterId("prod-us-east-1")
                .setName("production-cluster")
                .setMetadata(metadata)
                .addAllowedKafkaTopics("custom-topic-1")
                .addAllowedKafkaTopics("custom-topic-2")
                .addAllowedGrpcServices("external-service-1")
                .addAllowedGrpcServices("external-service-2")
                .build();

        assertEquals("prod-us-east-1", cluster.getClusterId());
        assertEquals("production-cluster", cluster.getName());
        assertEquals(2, cluster.getAllowedKafkaTopicsCount());
        assertTrue(cluster.getAllowedKafkaTopicsList().containsAll(Set.of("custom-topic-1", "custom-topic-2")));
        assertEquals(2, cluster.getAllowedGrpcServicesCount());
        assertTrue(cluster.getAllowedGrpcServicesList().containsAll(Set.of("external-service-1", "external-service-2")));
        assertNotNull(cluster.getMetadata());
        assertEquals("production-cluster-meta", cluster.getMetadata().getName());
    }

    @Test
    public void testMinimalClusterConfig() {
        // Only ID and name set; lists should be empty by default
        Cluster cluster = Cluster.newBuilder()
                .setClusterId("minimal-id")
                .setName("minimal-cluster")
                .build();

        assertEquals("minimal-id", cluster.getClusterId());
        assertEquals("minimal-cluster", cluster.getName());
        assertEquals(0, cluster.getAllowedKafkaTopicsCount());
        assertEquals(0, cluster.getAllowedGrpcServicesCount());
        // Metadata is default instance unless set
        assertEquals(ClusterMetadata.getDefaultInstance(), cluster.getMetadata());
    }

    @Test
    public void testJsonRoundTripWithJsonFormat() throws Exception {
        Cluster original = Cluster.newBuilder()
                .setClusterId("cluster-rt")
                .setName("roundtrip-cluster")
                .addAllowedKafkaTopics("topic-a")
                .addAllowedKafkaTopics("topic-b")
                .addAllowedGrpcServices("svc-x")
                .addAllowedGrpcServices("svc-y")
                .build();

        String json = JsonFormat.printer().includingDefaultValueFields().print(original);
        assertTrue(json.contains("\"clusterId\":"));
        assertTrue(json.contains("\"allowedKafkaTopics\""));
        assertTrue(json.contains("\"allowedGrpcServices\""));

        Cluster.Builder builder = Cluster.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
        Cluster parsed = builder.build();

        assertEquals(original.getClusterId(), parsed.getClusterId());
        assertEquals(original.getName(), parsed.getName());
        assertEquals(new HashSet<>(original.getAllowedKafkaTopicsList()), new HashSet<>(parsed.getAllowedKafkaTopicsList()));
        assertEquals(new HashSet<>(original.getAllowedGrpcServicesList()), new HashSet<>(parsed.getAllowedGrpcServicesList()));
    }

    @Test
    public void testRealWorldClusterConfiguration() throws Exception {
        Cluster real = Cluster.newBuilder()
                .setClusterId("rokkon-prod-us-east-1")
                .setName("rokkon-prod-us-east-1")
                .addAllowedKafkaTopics("external.documents.input")
                .addAllowedKafkaTopics("external.events.stream")
                .addAllowedKafkaTopics("audit.all-events")
                .addAllowedKafkaTopics("monitoring.metrics")
                .addAllowedKafkaTopics("cross-region.sync")
                .addAllowedGrpcServices("external-ocr-service")
                .addAllowedGrpcServices("external-translation-api")
                .addAllowedGrpcServices("legacy-search-service")
                .addAllowedGrpcServices("third-party-enrichment")
                .build();

        assertEquals("rokkon-prod-us-east-1", real.getClusterId());
        assertEquals(5, real.getAllowedKafkaTopicsCount());
        assertEquals(4, real.getAllowedGrpcServicesCount());

        // Round-trip via JSON
        String json = JsonFormat.printer().print(real);
        Cluster.Builder b = Cluster.newBuilder();
        JsonFormat.parser().merge(json, b);
        Cluster roundTrip = b.build();
        assertEquals(real, roundTrip);
    }
}