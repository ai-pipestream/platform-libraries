package ai.pipestream.api.events;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ModuleRegistrationRequestEventTest {

    @Test
    void testRecordCreation() {
        Map<String, String> metadata = Map.of("key1", "value1", "key2", "value2");
        
        ModuleRegistrationRequestEvent event = new ModuleRegistrationRequestEvent(
            "test-module",
            "impl-123",
            "localhost",
            8080,
            "GRPC",
            "1.0.0",
            metadata,
            "engine-host",
            49000,
            "{\"type\": \"object\"}",
            "req-123"
        );

        assertEquals("test-module", event.moduleName());
        assertEquals("impl-123", event.implementationId());
        assertEquals("localhost", event.host());
        assertEquals(8080, event.port());
        assertEquals("GRPC", event.serviceType());
        assertEquals("1.0.0", event.version());
        assertEquals(metadata, event.metadata());
        assertEquals("engine-host", event.engineHost());
        assertEquals(49000, event.enginePort());
        assertEquals("{\"type\": \"object\"}", event.jsonSchema());
        assertEquals("req-123", event.requestId());
    }

    @Test
    void testCreateMethodWithNonNullMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("region", "us-west");
        metadata.put("env", "prod");
        
        ModuleRegistrationRequestEvent event = ModuleRegistrationRequestEvent.create(
            "processor-module",
            "proc-456",
            "10.0.0.1",
            9090,
            "HTTP",
            "2.1.0",
            metadata,
            "engine.example.com",
            50000,
            null,
            "request-456"
        );

        assertTrue(event.metadata().containsKey("region"));
        assertTrue(event.metadata().containsKey("env"));
        assertEquals("us-west", event.metadata().get("region"));
        assertEquals("prod", event.metadata().get("env"));
        assertNotSame(metadata, event.metadata()); // Should be a copy
        
        // Verify immutability - changes to original map don't affect event
        metadata.put("new-key", "new-value");
        assertFalse(event.metadata().containsKey("new-key"));
    }

    @Test
    void testCreateMethodWithNullMetadata() {
        ModuleRegistrationRequestEvent event = ModuleRegistrationRequestEvent.create(
            "null-metadata-module",
            "impl-789",
            "192.168.1.1",
            7070,
            "GRPC",
            "3.0.0",
            null,
            "engine-local",
            49000,
            "{}",
            "req-789"
        );

        assertNotNull(event.metadata());
        assertTrue(event.metadata().isEmpty());
    }

    @Test
    void testRecordEquality() {
        Map<String, String> metadata = Map.of("test", "data");
        
        ModuleRegistrationRequestEvent event1 = ModuleRegistrationRequestEvent.create(
            "module",
            "impl",
            "host",
            8080,
            "GRPC",
            "1.0",
            metadata,
            "engine",
            49000,
            "schema",
            "req-1"
        );
        
        ModuleRegistrationRequestEvent event2 = ModuleRegistrationRequestEvent.create(
            "module",
            "impl",
            "host",
            8080,
            "GRPC",
            "1.0",
            metadata,
            "engine",
            49000,
            "schema",
            "req-1"
        );
        
        ModuleRegistrationRequestEvent event3 = ModuleRegistrationRequestEvent.create(
            "module",
            "impl",
            "host",
            8080,
            "GRPC",
            "1.0",
            metadata,
            "engine",
            49000,
            "schema",
            "req-2" // Different request ID
        );

        assertEquals(event2, event1);
        assertNotEquals(event3, event1);
        assertEquals(event2.hashCode(), event1.hashCode());
    }

    @Test
    void testToString() {
        ModuleRegistrationRequestEvent event = ModuleRegistrationRequestEvent.create(
            "string-test-module",
            "impl-999",
            "test-host",
            3000,
            "REST",
            "0.1.0",
            Map.of("key", "value"),
            "engine-host",
            49000,
            "schema",
            "req-999"
        );

        String eventString = event.toString();
        assertTrue(eventString.contains("string-test-module"));
        assertTrue(eventString.contains("impl-999"));
        assertTrue(eventString.contains("test-host"));
        assertTrue(eventString.contains("3000"));
        assertTrue(eventString.contains("req-999"));
    }

    @Test
    void testNullValuesInFields() {
        // Test that null values are handled appropriately
        ModuleRegistrationRequestEvent event = ModuleRegistrationRequestEvent.create(
            null,  // null module name
            null,  // null implementation ID
            null,  // null host
            0,     // zero port
            null,  // null service type
            null,  // null version
            null,  // null metadata
            null,  // null engine host
            0,     // zero engine port
            null,  // null json schema
            null   // null request ID
        );

        assertNull(event.moduleName());
        assertNull(event.implementationId());
        assertNull(event.host());
        assertEquals(0, event.port());
        assertNull(event.serviceType());
        assertNull(event.version());
        assertTrue(event.metadata().isEmpty()); // null metadata becomes empty map
        assertNull(event.engineHost());
        assertEquals(0, event.enginePort());
        assertNull(event.jsonSchema());
        assertNull(event.requestId());
    }
}