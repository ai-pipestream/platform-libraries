package ai.pipestream.api.events;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ModuleRegistrationResponseEventTest {

    @Test
    void testSuccessFactoryMethod() {
        ModuleRegistrationResponseEvent event = ModuleRegistrationResponseEvent.success(
            "req-123",
            "module-456",
            "test-module",
            "Module registered successfully"
        );

        assertEquals("req-123", event.requestId());
        assertTrue(event.success());
        assertEquals("Module registered successfully", event.message());
        assertEquals("module-456", event.moduleId());
        assertEquals("test-module", event.moduleName());
        assertNull(event.error());
    }

    @Test
    void testFailureFactoryMethod() {
        ModuleRegistrationResponseEvent event = ModuleRegistrationResponseEvent.failure(
            "req-789",
            "Connection refused to Consul"
        );

        assertEquals("req-789", event.requestId());
        assertFalse(event.success());
        assertEquals("Registration failed", event.message());
        assertNull(event.moduleId());
        assertNull(event.moduleName());
        assertEquals("Connection refused to Consul", event.error());
    }

    @Test
    void testDirectRecordCreation() {
        ModuleRegistrationResponseEvent event = new ModuleRegistrationResponseEvent(
            "req-custom",
            true,
            "Custom message",
            "module-custom",
            "custom-module",
            null
        );

        assertEquals("req-custom", event.requestId());
        assertTrue(event.success());
        assertEquals("Custom message", event.message());
        assertEquals("module-custom", event.moduleId());
        assertEquals("custom-module", event.moduleName());
        assertNull(event.error());
    }

    @Test
    void testRecordEquality() {
        ModuleRegistrationResponseEvent event1 = ModuleRegistrationResponseEvent.success(
            "req-1",
            "module-1",
            "module",
            "Success"
        );
        
        ModuleRegistrationResponseEvent event2 = ModuleRegistrationResponseEvent.success(
            "req-1",
            "module-1",
            "module",
            "Success"
        );
        
        ModuleRegistrationResponseEvent event3 = ModuleRegistrationResponseEvent.success(
            "req-2", // Different request ID
            "module-1",
            "module",
            "Success"
        );

        assertEquals(event2, event1);
        assertNotEquals(event3, event1);
        assertEquals(event2.hashCode(), event1.hashCode());
    }

    @Test
    void testToString() {
        ModuleRegistrationResponseEvent successEvent = ModuleRegistrationResponseEvent.success(
            "req-toString",
            "module-toString",
            "toString-module",
            "Test message"
        );

        String eventString = successEvent.toString();
        assertTrue(eventString.contains("req-toString"));
        assertTrue(eventString.contains("true")); // success=true
        assertTrue(eventString.contains("module-toString"));
        assertTrue(eventString.contains("toString-module"));
        assertTrue(eventString.contains("Test message"));
    }

    @Test
    void testFailureWithNullError() {
        ModuleRegistrationResponseEvent event = ModuleRegistrationResponseEvent.failure(
            "req-null-error",
            null
        );

        assertEquals("req-null-error", event.requestId());
        assertFalse(event.success());
        assertNull(event.error());
        assertNull(event.moduleId());
        assertNull(event.moduleName());
    }

    @Test
    void testSuccessWithNullValues() {
        ModuleRegistrationResponseEvent event = ModuleRegistrationResponseEvent.success(
            null,  // null request ID
            null,  // null module ID
            null,  // null module name
            null   // null message
        );

        assertNull(event.requestId());
        assertTrue(event.success());
        assertNull(event.message());
        assertNull(event.moduleId());
        assertNull(event.moduleName());
        assertNull(event.error());
    }

    @Test
    void testMixedStateEvent() {
        // Test a contradictory state (success=false but has moduleId)
        ModuleRegistrationResponseEvent event = new ModuleRegistrationResponseEvent(
            "req-mixed",
            false,  // failure
            "Mixed state",
            "module-mixed",  // has module ID despite failure
            "mixed-module",
            "Some error"
        );

        assertFalse(event.success());
        assertEquals("module-mixed", event.moduleId());
        assertEquals("Some error", event.error());
        // This shows the record allows inconsistent states - might want validation in production
    }
}