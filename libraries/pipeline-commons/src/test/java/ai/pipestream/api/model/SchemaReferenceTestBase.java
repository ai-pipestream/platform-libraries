package ai.pipestream.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.util.JsonFormat;
import ai.pipestream.config.v1.SchemaReference;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Updated base test class for SchemaReference (gRPC) that contains all test logic.
 * Critical for schema registry integration and version management.
 * Now tests the gRPC SchemaReference message instead of the old Java record.
 */
public abstract class SchemaReferenceTestBase {

    private static final Logger log = Logger.getLogger(SchemaReferenceTestBase.class);

    // Kept for compatibility with subclasses; not used in these gRPC-based tests
    protected abstract ObjectMapper getObjectMapper();

    @Test
    public void testBasicSchemaReference() throws Exception {
        SchemaReference ref = SchemaReference.newBuilder()
                .setSubject("test-schema")
                .setVersion(2)
                .build();
        
        assertEquals("test-schema", ref.getSubject(), "Subject should be set correctly");
        assertEquals(2, ref.getVersion(), "Version should be set correctly");

        String json = JsonFormat.printer().includingDefaultValueFields().print(ref);
        log.infof("SchemaReference JSON: %s", json);
        
        // Use more flexible JSON assertions
        assertTrue(json.contains("subject"), "JSON should contain subject field");
        assertTrue(json.contains("test-schema"), "JSON should contain the subject value");
        assertTrue(json.contains("version"), "JSON should contain version field");
        assertTrue(json.contains("2"), "JSON should contain the version value");
    }

    @Test
    public void testJsonRoundTrip() throws Exception {
        SchemaReference original = SchemaReference.newBuilder()
                .setSubject("test-schema")
                .setVersion(2)
                .build();

        String json = JsonFormat.printer().print(original);
        SchemaReference.Builder builder = SchemaReference.newBuilder();
        JsonFormat.parser().merge(json, builder);
        SchemaReference parsed = builder.build();
        
        assertEquals(original.getSubject(), parsed.getSubject());
        assertEquals(original.getVersion(), parsed.getVersion());
    }

    @Test
    public void testComplexSchemaName() {
        SchemaReference ref = SchemaReference.newBuilder()
                .setSubject("my-schema")
                .setVersion(5)
                .build();
        
        assertEquals("my-schema", ref.getSubject());
        assertEquals(5, ref.getVersion());

        SchemaReference complexRef = SchemaReference.newBuilder()
                .setSubject("com.rokkon.chunker-config-v2")
                .setVersion(10)
                .build();
        
        assertEquals("com.rokkon.chunker-config-v2", complexRef.getSubject());
        assertEquals(10, complexRef.getVersion());
    }

    @Test
    public void testValidSchemaReferences() {
        // Test various valid schema reference patterns
        assertDoesNotThrow(() -> SchemaReference.newBuilder()
                .setSubject("valid-subject")
                .setVersion(1)
                .build());
        assertDoesNotThrow(() -> SchemaReference.newBuilder()
                .setSubject("another-subject")
                .setVersion(10)
                .build());
        assertDoesNotThrow(() -> SchemaReference.newBuilder()
                .setSubject("com.example.schema")
                .setVersion(999)
                .build());
    }

    @Test
    public void testDefaultValues() {
        // Test default values for gRPC message
        SchemaReference defaultRef = SchemaReference.newBuilder().build();
        assertEquals("", defaultRef.getSubject());
        assertEquals(0, defaultRef.getVersion());
    }

    @Test
    public void testCompleteRoundTrip() throws Exception {
        SchemaReference original = SchemaReference.newBuilder()
                .setSubject("round-trip-test")
                .setVersion(3)
                .build();

        String json = JsonFormat.printer().includingDefaultValueFields().print(original);
        SchemaReference.Builder builder = SchemaReference.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
        SchemaReference deserialized = builder.build();
        
        assertEquals(original.getSubject(), deserialized.getSubject());
        assertEquals(original.getVersion(), deserialized.getVersion());
        assertEquals(original, deserialized);
    }

    @Test
    public void testSchemaReferenceEquality() {
        SchemaReference ref1 = SchemaReference.newBuilder()
                .setSubject("ordering-test")
                .setVersion(42)
                .build();
        
        SchemaReference ref2 = SchemaReference.newBuilder()
                .setSubject("ordering-test")
                .setVersion(42)
                .build();
        
        SchemaReference ref3 = SchemaReference.newBuilder()
                .setSubject("different-subject")
                .setVersion(42)
                .build();
        
        assertEquals(ref1, ref2);
        assertNotEquals(ref1, ref3);
        assertEquals(ref1.hashCode(), ref2.hashCode());
    }

    @Test
    public void testMultipleSchemaSubjects() throws Exception {
        String[] subjects = {
            "com.rokkon.parser.config",
            "com.rokkon.chunker.sliding-window",
            "com.rokkon.embedder.bert-base",
            "simple-schema",
            "another.complex.schema-name"
        };
        
        for (String subject : subjects) {
            SchemaReference ref = SchemaReference.newBuilder()
                    .setSubject(subject)
                    .setVersion(1)
                    .build();
            
            String json = JsonFormat.printer().print(ref);
            SchemaReference.Builder builder = SchemaReference.newBuilder();
            JsonFormat.parser().merge(json, builder);
            SchemaReference deserialized = builder.build();
            
            assertEquals(subject, deserialized.getSubject());
            assertEquals(1, deserialized.getVersion());
        }
    }

    @Test
    public void testVersionNumbers() throws Exception {
        String subject = "version-test-schema";
        int[] versions = {1, 5, 10, 42, 100, 999, 1000};
        
        for (int version : versions) {
            SchemaReference ref = SchemaReference.newBuilder()
                    .setSubject(subject)
                    .setVersion(version)
                    .build();
            
            assertEquals(version, ref.getVersion());
            
            String json = JsonFormat.printer().print(ref);
            SchemaReference.Builder builder = SchemaReference.newBuilder();
            JsonFormat.parser().merge(json, builder);
            SchemaReference deserialized = builder.build();
            
            assertEquals(version, deserialized.getVersion());
            assertEquals(subject, deserialized.getSubject());
        }
    }

    @Test
    public void testSchemaReferenceInModuleDefinition() throws Exception {
        // Test integration with ModuleDefinition
        SchemaReference schemaRef = SchemaReference.newBuilder()
                .setSubject("chunker-config-v1")
                .setVersion(3)
                .build();
        
        ai.pipestream.config.v1.ModuleDefinition module = ai.pipestream.config.v1.ModuleDefinition.newBuilder()
                .setModuleId("chunker-v1")
                .setImplementationName("SlidingWindowChunker")
                .setGrpcServiceName("chunker-service")
                .setConfigSchema(schemaRef)
                .build();
        
        assertEquals(schemaRef, module.getConfigSchema(), "Module should have the schema reference");
        assertEquals("chunker-config-v1", module.getConfigSchema().getSubject(), "Schema subject should match");
        assertEquals(3, module.getConfigSchema().getVersion(), "Schema version should match");
        
        String json = JsonFormat.printer().print(module);
        log.infof("ModuleDefinition JSON: %s", json);
        
        // Use more flexible JSON assertions
        assertTrue(json.contains("configSchema"), "JSON should contain configSchema field");
        assertTrue(json.contains("chunker-config-v1"), "JSON should contain the subject");
        assertTrue(json.contains("3"), "JSON should contain the version");
    }

    @Test
    public void testSchemaReferenceBuilder() {
        // Test builder pattern
        SchemaReference.Builder builder = SchemaReference.newBuilder();
        
        SchemaReference ref = builder
                .setSubject("builder-test")
                .setVersion(5)
                .build();
        
        assertEquals("builder-test", ref.getSubject());
        assertEquals(5, ref.getVersion());
        
        // Test builder reuse
        SchemaReference ref2 = builder
                .setSubject("builder-test-2")
                .setVersion(10)
                .build();
        
        assertEquals("builder-test-2", ref2.getSubject());
        assertEquals(10, ref2.getVersion());
    }

    @Test
    public void testSchemaReferenceToString() {
        SchemaReference ref = SchemaReference.newBuilder()
                .setSubject("toString-test")
                .setVersion(7)
                .build();
        
        String toString = ref.toString();
        assertTrue(toString.contains("toString-test"));
        assertTrue(toString.contains("7"));
    }
}
