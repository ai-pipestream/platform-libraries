package ai.pipestream.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.util.JsonFormat;
import ai.pipestream.config.v1.GraphEdge;
import ai.pipestream.config.v1.PipelineGraph;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Updated base test class for PipelineGraph (gRPC) that contains all test logic.
 * The legacy PipelineGraphConfig/PipelineConfig/Step models are replaced by PipelineGraph + GraphEdges.
 */
public abstract class PipelineGraphConfigTestBase {

    private static final Logger log = Logger.getLogger(PipelineGraphConfigTestBase.class);

    // Kept for compatibility with subclasses; not used in these gRPC-based tests
    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testEmptyGraphSerialization() throws Exception {
        PipelineGraph emptyGraph = PipelineGraph.newBuilder()
                .setGraphId("g-empty")
                .setClusterId("cluster-1")
                .setName("Empty Graph")
                .build();

        String json = JsonFormat.printer().includingDefaultValueFields().print(emptyGraph);
        log.infof("Empty graph JSON: %s", json);
        
        // Use more flexible JSON assertions - gRPC JSON format may vary
        assertTrue(json.contains("graphId"), "JSON should contain graphId field");
        assertTrue(json.contains("nodeIds"), "JSON should contain nodeIds field");
        assertTrue(json.contains("edges"), "JSON should contain edges field");

        PipelineGraph.Builder b = PipelineGraph.newBuilder();
        JsonFormat.parser().merge(json, b);
        PipelineGraph rt = b.build();
        assertEquals(emptyGraph, rt, "Round-trip should preserve the graph");
    }

    @Test
    public void testSingleNodeGraphSerialization() throws Exception {
        PipelineGraph graph = PipelineGraph.newBuilder()
                .setGraphId("g-1")
                .setClusterId("cluster-1")
                .setName("Single Node Graph")
                .addNodeIds("cluster-1.node-A")
                .build();

        String json = JsonFormat.printer().print(graph);
        log.infof("Single node graph JSON: %s", json);
        
        // Use more flexible JSON assertions
        assertTrue(json.contains("nodeIds"), "JSON should contain nodeIds field");
        assertTrue(json.contains("cluster-1.node-A"), "JSON should contain the node ID");

        PipelineGraph.Builder b = PipelineGraph.newBuilder();
        JsonFormat.parser().merge(json, b);
        assertEquals(graph, b.build(), "Round-trip should preserve the graph");
    }

    @Test
    public void testGraphWithMultipleNodesAndEdges() throws Exception {
        GraphEdge edge1 = GraphEdge.newBuilder()
                .setEdgeId("e-1")
                .setFromNodeId("cluster-1.node-A")
                .setToNodeId("cluster-1.node-B")
                .setPriority(1)
                .build();

        GraphEdge edge2 = GraphEdge.newBuilder()
                .setEdgeId("e-2")
                .setFromNodeId("cluster-1.node-A")
                .setToNodeId("cluster-1.node-C")
                .setPriority(2)
                .build();

        PipelineGraph graph = PipelineGraph.newBuilder()
                .setGraphId("g-2")
                .setClusterId("cluster-1")
                .setName("Branching Graph")
                .addNodeIds("cluster-1.node-A")
                .addNodeIds("cluster-1.node-B")
                .addNodeIds("cluster-1.node-C")
                .addEdges(edge1)
                .addEdges(edge2)
                .build();

        assertEquals(3, graph.getNodeIdsCount());
        assertEquals(2, graph.getEdgesCount());

        String json = JsonFormat.printer().print(graph);
        PipelineGraph.Builder b = PipelineGraph.newBuilder();
        JsonFormat.parser().merge(json, b);
        assertEquals(graph, b.build());
    }

    @Test
    public void testNullHandlingDefaults() {
        // Protobuf builders default collections to empty and primitives to defaults
        PipelineGraph graph = PipelineGraph.newBuilder().build();
        assertEquals("", graph.getGraphId());
        assertEquals(0, graph.getNodeIdsCount());
        assertEquals(0, graph.getEdgesCount());
    }

    @Test
    public void testComplexGraphRoundTrip() throws Exception {
        GraphEdge edge = GraphEdge.newBuilder()
                .setEdgeId("e-rt")
                .setFromNodeId("c1.node-A")
                .setToNodeId("c1.node-B")
                .setPriority(1)
                .build();

        PipelineGraph original = PipelineGraph.newBuilder()
                .setGraphId("g-rt")
                .setClusterId("c1")
                .setName("Round Trip")
                .addNodeIds("c1.node-A")
                .addNodeIds("c1.node-B")
                .addEdges(edge)
                .build();

        String json = JsonFormat.printer().includingDefaultValueFields().print(original);
        PipelineGraph.Builder b = PipelineGraph.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(json, b);
        PipelineGraph parsed = b.build();

        assertEquals(original, parsed);
    }
}