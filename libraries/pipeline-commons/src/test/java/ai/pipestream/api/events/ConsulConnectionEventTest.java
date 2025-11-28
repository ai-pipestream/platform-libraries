package ai.pipestream.api.events;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ConsulConnectionEventTest {

    @Test
    void testConnectedEvent() {
        ConsulConnectionEvent.ConsulConnectionConfig config = 
            new ConsulConnectionEvent.ConsulConnectionConfig("consul.example.com", 8500, true);
        
        ConsulConnectionEvent event = new ConsulConnectionEvent(
            ConsulConnectionEvent.Type.CONNECTED,
            config,
            "Successfully connected to Consul"
        );

        assertEquals(ConsulConnectionEvent.Type.CONNECTED, event.getType());
        assertEquals(config, event.getConfig());
        assertEquals("Successfully connected to Consul", event.getMessage());
    }

    @Test
    void testDisconnectedEvent() {
        ConsulConnectionEvent.ConsulConnectionConfig config = 
            new ConsulConnectionEvent.ConsulConnectionConfig("localhost", 8500, false);
        
        ConsulConnectionEvent event = new ConsulConnectionEvent(
            ConsulConnectionEvent.Type.DISCONNECTED,
            config,
            "Lost connection to Consul"
        );

        assertEquals(ConsulConnectionEvent.Type.DISCONNECTED, event.getType());
        assertEquals(config, event.getConfig());
        assertEquals("Lost connection to Consul", event.getMessage());
    }

    @Test
    void testConnectionFailedEvent() {
        ConsulConnectionEvent.ConsulConnectionConfig config = 
            new ConsulConnectionEvent.ConsulConnectionConfig("consul.local", 8500, false);
        
        ConsulConnectionEvent event = new ConsulConnectionEvent(
            ConsulConnectionEvent.Type.CONNECTION_FAILED,
            config,
            "Connection refused: consul.local:8500"
        );

        assertEquals(ConsulConnectionEvent.Type.CONNECTION_FAILED, event.getType());
        assertEquals(config, event.getConfig());
        assertEquals("Connection refused: consul.local:8500", event.getMessage());
    }

    @Test
    void testConsulConnectionConfig() {
        ConsulConnectionEvent.ConsulConnectionConfig config = 
            new ConsulConnectionEvent.ConsulConnectionConfig("192.168.1.100", 8501, true);

        assertEquals("192.168.1.100", config.host());
        assertEquals(8501, config.port());
        assertTrue(config.connected());
    }

    @Test
    void testConfigEquality() {
        ConsulConnectionEvent.ConsulConnectionConfig config1 = 
            new ConsulConnectionEvent.ConsulConnectionConfig("host1", 8500, true);
        
        ConsulConnectionEvent.ConsulConnectionConfig config2 = 
            new ConsulConnectionEvent.ConsulConnectionConfig("host1", 8500, true);
        
        ConsulConnectionEvent.ConsulConnectionConfig config3 = 
            new ConsulConnectionEvent.ConsulConnectionConfig("host2", 8500, true);

        assertEquals(config2, config1);
        assertNotEquals(config3, config1);
        assertEquals(config2.hashCode(), config1.hashCode());
    }

    @Test
    void testConfigToString() {
        ConsulConnectionEvent.ConsulConnectionConfig config = 
            new ConsulConnectionEvent.ConsulConnectionConfig("consul-server", 8500, false);

        String configString = config.toString();
        assertTrue(configString.contains("consul-server"));
        assertTrue(configString.contains("8500"));
        assertTrue(configString.contains("false"));
    }

    @Test
    void testEventWithNullValues() {
        ConsulConnectionEvent event = new ConsulConnectionEvent(
            ConsulConnectionEvent.Type.CONNECTED,
            null,  // null config
            null   // null message
        );

        assertEquals(ConsulConnectionEvent.Type.CONNECTED, event.getType());
        assertNull(event.getConfig());
        assertNull(event.getMessage());
    }

    @Test
    void testAllEventTypes() {
        // Verify all enum values are accessible
        ConsulConnectionEvent.Type[] types = ConsulConnectionEvent.Type.values();
        
        assertArrayEquals(
            new ConsulConnectionEvent.Type[] {
                ConsulConnectionEvent.Type.CONNECTED,
                ConsulConnectionEvent.Type.DISCONNECTED,
                ConsulConnectionEvent.Type.CONNECTION_FAILED
            },
            types
        );
    }

    @Test
    void testEventTypeValueOf() {
        assertEquals(
            ConsulConnectionEvent.Type.CONNECTED,
            ConsulConnectionEvent.Type.valueOf("CONNECTED")
        );
        assertEquals(
            ConsulConnectionEvent.Type.DISCONNECTED,
            ConsulConnectionEvent.Type.valueOf("DISCONNECTED")
        );
        assertEquals(
            ConsulConnectionEvent.Type.CONNECTION_FAILED,
            ConsulConnectionEvent.Type.valueOf("CONNECTION_FAILED")
        );
    }

    @Test
    void testConfigWithZeroPort() {
        ConsulConnectionEvent.ConsulConnectionConfig config = 
            new ConsulConnectionEvent.ConsulConnectionConfig("consul", 0, false);

        assertEquals(0, config.port());
    }

    @Test
    void testConfigWithNullHost() {
        ConsulConnectionEvent.ConsulConnectionConfig config = 
            new ConsulConnectionEvent.ConsulConnectionConfig(null, 8500, true);

        assertNull(config.host());
        assertEquals(8500, config.port());
        assertTrue(config.connected());
    }
}