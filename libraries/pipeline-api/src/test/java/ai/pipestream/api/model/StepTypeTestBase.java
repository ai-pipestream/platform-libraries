package ai.pipestream.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import ai.pipestream.config.v1.NodeType;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Updated base test class for NodeType enum behavior testing.
 * Tests all node types used in pipeline configuration using gRPC enums.
 * NodeType replaces the old StepType enum.
 */
public abstract class StepTypeTestBase {

    private static final Logger log = Logger.getLogger(StepTypeTestBase.class);

    // Kept for compatibility with subclasses; not used in these gRPC-based tests
    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testProcessorEnumValue() {
        // NodeType.NODE_TYPE_PROCESSOR replaces old StepType.PIPELINE
        assertEquals("NODE_TYPE_PROCESSOR", NodeType.NODE_TYPE_PROCESSOR.name());
        assertEquals(2, NodeType.NODE_TYPE_PROCESSOR.getNumber());
    }

    @Test
    public void testConnectorEnumValue() {
        // NodeType.NODE_TYPE_CONNECTOR replaces old StepType.CONNECTOR
        assertEquals("NODE_TYPE_CONNECTOR", NodeType.NODE_TYPE_CONNECTOR.name());
        assertEquals(1, NodeType.NODE_TYPE_CONNECTOR.getNumber());
    }

    @Test
    public void testSinkEnumValue() {
        // NodeType.NODE_TYPE_SINK replaces old StepType.SINK
        assertEquals("NODE_TYPE_SINK", NodeType.NODE_TYPE_SINK.name());
        assertEquals(3, NodeType.NODE_TYPE_SINK.getNumber());
    }

    @Test
    public void testUnspecifiedEnumValue() {
        // gRPC enums have UNSPECIFIED as default value
        assertEquals("NODE_TYPE_UNSPECIFIED", NodeType.NODE_TYPE_UNSPECIFIED.name());
        assertEquals(0, NodeType.NODE_TYPE_UNSPECIFIED.getNumber());
    }

    @Test
    public void testAllValues() {
        NodeType[] values = NodeType.values();
        log.infof("NodeType enum has %d values", values.length);
        
        // gRPC enums include UNSPECIFIED as first value and UNRECOGNIZED as last (5 total)
        assertEquals(5, values.length, "NodeType should have 5 values (4 defined + UNRECOGNIZED)");
        assertEquals(NodeType.NODE_TYPE_UNSPECIFIED, values[0], "First value should be UNSPECIFIED");
        assertEquals(NodeType.NODE_TYPE_CONNECTOR, values[1], "Second value should be CONNECTOR");
        assertEquals(NodeType.NODE_TYPE_PROCESSOR, values[2], "Third value should be PROCESSOR");
        assertEquals(NodeType.NODE_TYPE_SINK, values[3], "Fourth value should be SINK");
        assertEquals(NodeType.UNRECOGNIZED, values[4], "Fifth value should be UNRECOGNIZED");
    }

    @Test
    public void testEnumStringRepresentation() {
        // Test string representations for all enum values
        assertEquals("NODE_TYPE_UNSPECIFIED", NodeType.NODE_TYPE_UNSPECIFIED.toString());
        assertEquals("NODE_TYPE_CONNECTOR", NodeType.NODE_TYPE_CONNECTOR.toString());
        assertEquals("NODE_TYPE_PROCESSOR", NodeType.NODE_TYPE_PROCESSOR.toString());
        assertEquals("NODE_TYPE_SINK", NodeType.NODE_TYPE_SINK.toString());
    }

    @Test
    public void testInvalidDeserialization() {
        // Test invalid enum values using valueOf
        IllegalArgumentException invalidTypeException = assertThrows(IllegalArgumentException.class, 
            () -> NodeType.valueOf("INVALID_TYPE"));
        assertTrue(invalidTypeException.getMessage().contains("No enum constant"));
            
        IllegalArgumentException emptyStringException = assertThrows(IllegalArgumentException.class, 
            () -> NodeType.valueOf(""));
        assertTrue(emptyStringException.getMessage().contains("No enum constant"));
    }

    @Test
    public void testCaseSensitiveDeserialization() {
        // gRPC enums are case-sensitive
        Exception lowercaseException = assertThrows(Exception.class, 
            () -> NodeType.valueOf("node_type_processor"));
        assertTrue(lowercaseException.getMessage().contains("No enum constant"));
            
        Exception titlecaseException = assertThrows(Exception.class, 
            () -> NodeType.valueOf("Node_Type_Processor"));
        assertTrue(titlecaseException.getMessage().contains("No enum constant"));
    }

    @Test
    public void testEnumNumbers() {
        // Test that enum numbers match proto definition
        assertEquals(0, NodeType.NODE_TYPE_UNSPECIFIED.getNumber());
        assertEquals(1, NodeType.NODE_TYPE_CONNECTOR.getNumber());
        assertEquals(2, NodeType.NODE_TYPE_PROCESSOR.getNumber());
        assertEquals(3, NodeType.NODE_TYPE_SINK.getNumber());
    }

    @Test
    public void testForNumber() {
        // Test gRPC enum forNumber method
        assertEquals(NodeType.NODE_TYPE_UNSPECIFIED, NodeType.forNumber(0), "forNumber(0) should return UNSPECIFIED");
        assertEquals(NodeType.NODE_TYPE_CONNECTOR, NodeType.forNumber(1), "forNumber(1) should return CONNECTOR");
        assertEquals(NodeType.NODE_TYPE_PROCESSOR, NodeType.forNumber(2), "forNumber(2) should return PROCESSOR");
        assertEquals(NodeType.NODE_TYPE_SINK, NodeType.forNumber(3), "forNumber(3) should return SINK");
        
        // Invalid number returns null, not UNRECOGNIZED
        NodeType invalidResult = NodeType.forNumber(999);
        log.infof("NodeType.forNumber(999) returned: %s", invalidResult);
        assertNull(invalidResult, "forNumber(999) should return null for invalid number");
    }

    @Test
    public void testValueOf() {
        // Test standard enum valueOf behavior for gRPC enums
        assertEquals(NodeType.NODE_TYPE_PROCESSOR, NodeType.valueOf("NODE_TYPE_PROCESSOR"));
        assertEquals(NodeType.NODE_TYPE_CONNECTOR, NodeType.valueOf("NODE_TYPE_CONNECTOR"));
        assertEquals(NodeType.NODE_TYPE_SINK, NodeType.valueOf("NODE_TYPE_SINK"));
        assertEquals(NodeType.NODE_TYPE_UNSPECIFIED, NodeType.valueOf("NODE_TYPE_UNSPECIFIED"));
        
        // Test invalid enum value
        assertThrows(IllegalArgumentException.class, () -> NodeType.valueOf("INVALID"));
    }

    @Test
    public void testEnumInGraphNode() throws Exception {
        // Test enum usage within GraphNode (integration test)
        ai.pipestream.config.v1.GraphNode node = ai.pipestream.config.v1.GraphNode.newBuilder()
                .setNodeId("test.node")
                .setClusterId("test")
                .setName("test-node")
                .setNodeType(NodeType.NODE_TYPE_PROCESSOR)
                .setModuleId("test-module")
                .build();
        
        assertEquals(NodeType.NODE_TYPE_PROCESSOR, node.getNodeType());
        assertEquals("test.node", node.getNodeId());
        assertEquals("test-node", node.getName());
    }

    @Test
    public void testMappingFromOldStepType() {
        // Document the mapping from old StepType to new NodeType
        // Old StepType.PIPELINE -> NodeType.NODE_TYPE_PROCESSOR
        assertEquals("NODE_TYPE_PROCESSOR", NodeType.NODE_TYPE_PROCESSOR.name());
        
        // Old StepType.CONNECTOR -> NodeType.NODE_TYPE_CONNECTOR  
        assertEquals("NODE_TYPE_CONNECTOR", NodeType.NODE_TYPE_CONNECTOR.name());
        
        // Old StepType.SINK -> NodeType.NODE_TYPE_SINK
        assertEquals("NODE_TYPE_SINK", NodeType.NODE_TYPE_SINK.name());
        
        // New: NodeType.NODE_TYPE_UNSPECIFIED (no old equivalent)
        assertEquals("NODE_TYPE_UNSPECIFIED", NodeType.NODE_TYPE_UNSPECIFIED.name());
    }

    @Test
    public void testEnumComparison() {
        // Test enum comparison and ordering
        assertTrue(NodeType.NODE_TYPE_UNSPECIFIED.getNumber() < NodeType.NODE_TYPE_CONNECTOR.getNumber());
        assertTrue(NodeType.NODE_TYPE_CONNECTOR.getNumber() < NodeType.NODE_TYPE_PROCESSOR.getNumber());
        assertTrue(NodeType.NODE_TYPE_PROCESSOR.getNumber() < NodeType.NODE_TYPE_SINK.getNumber());
    }

    @Test
    public void testEnumEquality() {
        // Test enum equality
        assertEquals(NodeType.NODE_TYPE_PROCESSOR, NodeType.NODE_TYPE_PROCESSOR);
        assertNotEquals(NodeType.NODE_TYPE_PROCESSOR, NodeType.NODE_TYPE_CONNECTOR);
        assertNotEquals(NodeType.NODE_TYPE_PROCESSOR, NodeType.NODE_TYPE_SINK);
        assertNotEquals(NodeType.NODE_TYPE_PROCESSOR, NodeType.NODE_TYPE_UNSPECIFIED);
    }
}
