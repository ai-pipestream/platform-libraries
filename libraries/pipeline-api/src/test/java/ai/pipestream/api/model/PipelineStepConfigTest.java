package ai.pipestream.api.model;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import ai.pipestream.config.v1.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Updated test class for GraphNode (gRPC) that replaces the old PipelineStepConfig.
 * Tests the complete GraphNode configuration including transport, custom config, and node metadata.
 */
public class PipelineStepConfigTest {

    @Test
    public void testGraphNodeWithGrpcTransportRoundTrip() throws Exception {
        // Build JSON-like config params using Struct for complex config
        Struct jsonConfig = Struct.newBuilder()
                .putFields("timeout", Value.newBuilder().setStringValue("30s").build())
                .putFields("retries", Value.newBuilder().setNumberValue(3).build())
                .build();

        JsonConfigOptions opts = JsonConfigOptions.newBuilder()
                .setJsonConfig(jsonConfig)
                .putConfigParams("maxConnections", "10")
                .putConfigParams("poolSize", "5")
                .build();

        GrpcConfig grpc = GrpcConfig.newBuilder()
                .setServiceName("chunker-service")
                .putProperties("deadline", "60000")
                .putProperties("keepAlive", "true")
                .build();

        TransportConfig transport = TransportConfig.newBuilder()
                .setType(TransportType.TRANSPORT_TYPE_GRPC)
                .setGrpc(grpc)
                .setMaxRetries(3)
                .setRetryBackoffMs(2000)
                .setStepTimeoutMs(30000)
                .build();

        GraphNode node = GraphNode.newBuilder()
                .setNodeId("cluster-1.chunker-step")
                .setClusterId("cluster-1")
                .setName("chunker-step")
                .setNodeType(NodeType.NODE_TYPE_PROCESSOR)
                .setModuleId("chunker-module-v1")
                .setCustomConfig(opts)
                .setTransport(transport)
                .setVisibility(ClusterVisibility.CLUSTER_VISIBILITY_PUBLIC)
                .setMode(NodeMode.NODE_MODE_PRODUCTION)
                .setKafkaInputTopic("cluster-1.chunker-step")
                .setKafkaOutputTopic("cluster-1.embedder-step")
                .setRepositoryPath("/clusters/cluster-1/nodes/chunker-step")
                .setCreatedAt(System.currentTimeMillis())
                .setModifiedAt(System.currentTimeMillis())
                .build();

        String json = JsonFormat.printer().includingDefaultValueFields().print(node);
        assertTrue(json.contains("nodeId"));
        assertTrue(json.contains("grpc"));
        assertTrue(json.contains("customConfig"));

        GraphNode.Builder b = GraphNode.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(json, b);
        GraphNode parsed = b.build();
        assertEquals(node, parsed);
    }

    @Test
    public void testGraphNodeWithMessagingTransport() throws Exception {
        MessagingConfig messaging = MessagingConfig.newBuilder()
                .addSubscriptions("input-topic-1")
                .addSubscriptions("input-topic-2")
                .addTopics("output-topic-1")
                .setPartitionKeyField("documentId")
                .putProperties("auto.offset.reset", "earliest")
                .putProperties("group.id", "sink-consumer-group")
                .build();

        TransportConfig transport = TransportConfig.newBuilder()
                .setType(TransportType.TRANSPORT_TYPE_MESSAGING)
                .setMessaging(messaging)
                .setMaxRetries(5)
                .setRetryBackoffMs(1000)
                .setStepTimeoutMs(60000)
                .build();

        GraphNode node = GraphNode.newBuilder()
                .setNodeId("cluster-1.sink-step")
                .setClusterId("cluster-1")
                .setName("sink-step")
                .setNodeType(NodeType.NODE_TYPE_SINK)
                .setModuleId("opensearch-sink-v1")
                .setTransport(transport)
                .setVisibility(ClusterVisibility.CLUSTER_VISIBILITY_PRIVATE)
                .setMode(NodeMode.NODE_MODE_PRODUCTION)
                .build();

        String json = JsonFormat.printer().print(node);
        assertTrue(json.contains("messaging"));
        assertTrue(json.contains("subscriptions"));

        GraphNode.Builder b = GraphNode.newBuilder();
        JsonFormat.parser().merge(json, b);
        assertEquals(node, b.build());
    }

    @Test
    public void testComplexGraphNodeConfiguration() throws Exception {
        // Create complex JSON config using Struct
        Struct complexConfig = Struct.newBuilder()
                .putFields("chunkSize", Value.newBuilder().setNumberValue(1000).build())
                .putFields("overlap", Value.newBuilder().setNumberValue(100).build())
                .putFields("splitOnSentences", Value.newBuilder().setBoolValue(true).build())
                .putFields("metadata", Value.newBuilder()
                        .setStructValue(Struct.newBuilder()
                                .putFields("version", Value.newBuilder().setStringValue("v2.1").build())
                                .putFields("author", Value.newBuilder().setStringValue("pipeline-team").build())
                                .build())
                        .build())
                .build();

        JsonConfigOptions customConfig = JsonConfigOptions.newBuilder()
                .setJsonConfig(complexConfig)
                .putConfigParams("timeout", "30s")
                .putConfigParams("retries", "3")
                .putConfigParams("batchSize", "50")
                .build();

        // Create messaging config for inputs
        MessagingConfig messaging = MessagingConfig.newBuilder()
                .addSubscriptions("documents-input")
                .addSubscriptions("priority-documents")
                .addTopics("chunks-output")
                .addTopics("metadata-output")
                .setPartitionKeyField("documentId")
                .putProperties("auto.offset.reset", "earliest")
                .putProperties("max.poll.records", "500")
                .putProperties("session.timeout.ms", "30000")
                .build();

        TransportConfig transport = TransportConfig.newBuilder()
                .setType(TransportType.TRANSPORT_TYPE_MESSAGING)
                .setMessaging(messaging)
                .setMaxRetries(3)
                .setRetryBackoffMs(2000)
                .setStepTimeoutMs(30000)
                .build();

        GraphNode node = GraphNode.newBuilder()
                .setNodeId("cluster-1.advanced-chunker")
                .setClusterId("cluster-1")
                .setName("advanced-chunker")
                .setNodeType(NodeType.NODE_TYPE_PROCESSOR)
                .setModuleId("advanced-chunker-v2")
                .setCustomConfig(customConfig)
                .setTransport(transport)
                .setVisibility(ClusterVisibility.CLUSTER_VISIBILITY_PUBLIC)
                .setMode(NodeMode.NODE_MODE_PRODUCTION)
                .setKafkaInputTopic("cluster-1.advanced-chunker")
                .setKafkaOutputTopic("cluster-1.embedder-step")
                .setRepositoryPath("/clusters/cluster-1/nodes/advanced-chunker")
                .setCreatedAt(1700000000000L)
                .setModifiedAt(1700000001000L)
                .build();

        String json = JsonFormat.printer().includingDefaultValueFields().print(node);

        // Verify all major components are serialized
        assertTrue(json.contains("cluster-1.advanced-chunker"));
        assertTrue(json.contains("NODE_TYPE_PROCESSOR"));
        assertTrue(json.contains("1000"));
        assertTrue(json.contains("documents-input"));
        assertTrue(json.contains("chunks-output"));
        assertTrue(json.contains("3"));
        assertTrue(json.contains("advanced-chunker-v2"));

        // Test round trip
        GraphNode.Builder b = GraphNode.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(json, b);
        GraphNode parsed = b.build();
        assertEquals(node, parsed);
    }

    @Test
    public void testMinimalGraphNodeConfiguration() throws Exception {
        // Only required fields set
        GraphNode node = GraphNode.newBuilder()
                .setNodeId("cluster-1.simple-step")
                .setClusterId("cluster-1")
                .setName("simple-step")
                .setNodeType(NodeType.NODE_TYPE_PROCESSOR)
                .setModuleId("simple-processor-v1")
                .build();

        String json = JsonFormat.printer().print(node);
        assertTrue(json.contains("cluster-1.simple-step"));
        assertTrue(json.contains("NODE_TYPE_PROCESSOR"));
        assertTrue(json.contains("simple-processor-v1"));

        // Test round trip
        GraphNode.Builder b = GraphNode.newBuilder();
        JsonFormat.parser().merge(json, b);
        GraphNode deserialized = b.build();
        assertEquals("cluster-1.simple-step", deserialized.getNodeId());
        assertEquals(NodeType.NODE_TYPE_PROCESSOR, deserialized.getNodeType());
        assertEquals("simple-processor-v1", deserialized.getModuleId());
    }

    @Test
    public void testDesignModeGraphNode() throws Exception {
        // Create design mode configuration
        DesignModeConfig designConfig = DesignModeConfig.newBuilder()
                .setCanvasX(100)
                .setCanvasY(200)
                .setSimulateProcessing(true)
                .setSimulatedProcessingTimeMs(1500)
                .setSimulatedSuccessRate(0.95)
                .addSampleInputData("{\"text\":\"Sample document for testing\"}")
                .addExpectedOutputData("{\"chunks\":[\"Sample document\",\"for testing\"]}")
                .setUiColor("#4CAF50")
                .setUiIcon("processor-icon")
                .setUiCollapsed(false)
                .build();

        GraphNode designNode = GraphNode.newBuilder()
                .setNodeId("design.test-processor")
                .setClusterId("design")
                .setName("test-processor")
                .setNodeType(NodeType.NODE_TYPE_PROCESSOR)
                .setModuleId("test-module-v1")
                .setMode(NodeMode.NODE_MODE_DESIGN)
                .setDesignConfig(designConfig)
                .build();

        String json = JsonFormat.printer().includingDefaultValueFields().print(designNode);
        assertTrue(json.contains("NODE_MODE_DESIGN"));
        assertTrue(json.contains("100"));
        assertTrue(json.contains("true"));
        assertTrue(json.contains("#4CAF50"));

        // Test round trip
        GraphNode.Builder b = GraphNode.newBuilder();
        JsonFormat.parser().merge(json, b);
        GraphNode parsed = b.build();
        assertEquals(designNode, parsed);
        assertEquals(NodeMode.NODE_MODE_DESIGN, parsed.getMode());
        assertEquals(100, parsed.getDesignConfig().getCanvasX());
        assertEquals(0.95, parsed.getDesignConfig().getSimulatedSuccessRate(), 0.001);
    }

    @Test
    public void testNodeTypeValidation() {
        // Test all node types
        GraphNode connector = GraphNode.newBuilder()
                .setNodeId("test.connector")
                .setClusterId("test")
                .setName("connector")
                .setNodeType(NodeType.NODE_TYPE_CONNECTOR)
                .setModuleId("connector-v1")
                .build();
        assertEquals(NodeType.NODE_TYPE_CONNECTOR, connector.getNodeType());

        GraphNode processor = GraphNode.newBuilder()
                .setNodeId("test.processor")
                .setClusterId("test")
                .setName("processor")
                .setNodeType(NodeType.NODE_TYPE_PROCESSOR)
                .setModuleId("processor-v1")
                .build();
        assertEquals(NodeType.NODE_TYPE_PROCESSOR, processor.getNodeType());

        GraphNode sink = GraphNode.newBuilder()
                .setNodeId("test.sink")
                .setClusterId("test")
                .setName("sink")
                .setNodeType(NodeType.NODE_TYPE_SINK)
                .setModuleId("sink-v1")
                .build();
        assertEquals(NodeType.NODE_TYPE_SINK, sink.getNodeType());
    }

    @Test
    public void testTransportConfigValidation() throws Exception {
        // Test GRPC transport
        GrpcConfig grpcConfig = GrpcConfig.newBuilder()
                .setServiceName("test-service")
                .putProperties("timeout", "5000")
                .build();

        TransportConfig grpcTransport = TransportConfig.newBuilder()
                .setType(TransportType.TRANSPORT_TYPE_GRPC)
                .setGrpc(grpcConfig)
                .build();

        assertEquals(TransportType.TRANSPORT_TYPE_GRPC, grpcTransport.getType());
        assertEquals("test-service", grpcTransport.getGrpc().getServiceName());

        // Test Messaging transport
        MessagingConfig messagingConfig = MessagingConfig.newBuilder()
                .addTopics("output-topic")
                .addSubscriptions("input-topic")
                .build();

        TransportConfig messagingTransport = TransportConfig.newBuilder()
                .setType(TransportType.TRANSPORT_TYPE_MESSAGING)
                .setMessaging(messagingConfig)
                .build();

        assertEquals(TransportType.TRANSPORT_TYPE_MESSAGING, messagingTransport.getType());
        assertTrue(messagingTransport.getMessaging().getTopicsList().contains("output-topic"));
        assertTrue(messagingTransport.getMessaging().getSubscriptionsList().contains("input-topic"));
    }

    @Test
    public void testRoundTripComplexConfiguration() throws Exception {
        // Create the most complex configuration possible
        Struct complexJsonConfig = Struct.newBuilder()
                .putFields("setting1", Value.newBuilder().setStringValue("value1").build())
                .putFields("setting2", Value.newBuilder().setNumberValue(42).build())
                .putFields("nested", Value.newBuilder()
                        .setStructValue(Struct.newBuilder()
                                .putFields("innerSetting", Value.newBuilder().setBoolValue(true).build())
                                .build())
                        .build())
                .build();

        JsonConfigOptions customConfig = JsonConfigOptions.newBuilder()
                .setJsonConfig(complexJsonConfig)
                .putConfigParams("param1", "paramValue1")
                .putConfigParams("param2", "paramValue2")
                .build();

        MessagingConfig messaging = MessagingConfig.newBuilder()
                .addTopics("topic1")
                .addTopics("topic2")
                .addSubscriptions("sub1")
                .addSubscriptions("sub2")
                .setPartitionKeyField("key")
                .putProperties("prop1", "value1")
                .putProperties("prop2", "value2")
                .build();

        TransportConfig transport = TransportConfig.newBuilder()
                .setType(TransportType.TRANSPORT_TYPE_MESSAGING)
                .setMessaging(messaging)
                .setMaxRetries(2)
                .setRetryBackoffMs(1500)
                .setStepTimeoutMs(25000)
                .build();

        DesignModeConfig designConfig = DesignModeConfig.newBuilder()
                .setCanvasX(50)
                .setCanvasY(75)
                .setSimulateProcessing(true)
                .setSimulatedProcessingTimeMs(2000)
                .setSimulatedSuccessRate(0.9)
                .addSampleInputData("{\"test\":\"data\"}")
                .setUiColor("#FF5722")
                .setUiIcon("test-icon")
                .build();

        GraphNode original = GraphNode.newBuilder()
                .setNodeId("test.complex-node")
                .setClusterId("test")
                .setName("complex-node")
                .setNodeType(NodeType.NODE_TYPE_PROCESSOR)
                .setModuleId("complex-module-v1")
                .setCustomConfig(customConfig)
                .setTransport(transport)
                .setVisibility(ClusterVisibility.CLUSTER_VISIBILITY_RESTRICTED)
                .setMode(NodeMode.NODE_MODE_DESIGN)
                .setDesignConfig(designConfig)
                .setKafkaInputTopic("test.complex-node")
                .setKafkaOutputTopic("test.next-node")
                .setRepositoryPath("/clusters/test/nodes/complex-node")
                .setCreatedAt(1700000000000L)
                .setModifiedAt(1700000001000L)
                .build();

        // Serialize and deserialize
        String json = JsonFormat.printer().includingDefaultValueFields().print(original);
        GraphNode.Builder b = GraphNode.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(json, b);
        GraphNode deserialized = b.build();

        // Verify all fields match
        assertEquals(original.getNodeId(), deserialized.getNodeId());
        assertEquals(original.getClusterId(), deserialized.getClusterId());
        assertEquals(original.getName(), deserialized.getName());
        assertEquals(original.getNodeType(), deserialized.getNodeType());
        assertEquals(original.getModuleId(), deserialized.getModuleId());
        assertEquals(original.getVisibility(), deserialized.getVisibility());
        assertEquals(original.getMode(), deserialized.getMode());
        assertEquals(original.getKafkaInputTopic(), deserialized.getKafkaInputTopic());
        assertEquals(original.getKafkaOutputTopic(), deserialized.getKafkaOutputTopic());
        assertEquals(original.getRepositoryPath(), deserialized.getRepositoryPath());
        assertEquals(original.getCreatedAt(), deserialized.getCreatedAt());
        assertEquals(original.getModifiedAt(), deserialized.getModifiedAt());

        // Verify nested objects
        assertEquals(original.getTransport().getMaxRetries(), deserialized.getTransport().getMaxRetries());
        assertEquals(original.getTransport().getMessaging().getTopicsCount(), 
                    deserialized.getTransport().getMessaging().getTopicsCount());
        assertEquals(original.getCustomConfig().getConfigParamsCount(), 
                    deserialized.getCustomConfig().getConfigParamsCount());
        assertEquals(original.getDesignConfig().getCanvasX(), deserialized.getDesignConfig().getCanvasX());
        assertEquals(original.getDesignConfig().getSimulatedSuccessRate(), 
                    deserialized.getDesignConfig().getSimulatedSuccessRate(), 0.001);
    }
}
