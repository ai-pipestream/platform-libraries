package ai.pipestream.grpc.wiremock;

import ai.pipestream.repository.filesystem.upload.InitiateUploadRequest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProtoJson to verify protobuf to JSON serialization.
 * <p>
 * This helps isolate issues with request matching in WireMock,
 * particularly around default values and field inclusion.
 */
public class ProtoJsonTest {

    @Test
    void testToJson_IncludesAllFields() {
        // Create a request with all fields set
        InitiateUploadRequest request = InitiateUploadRequest.newBuilder()
            .setDrive("test-drive")
            .setParentId("parent-123")
            .setName("test-file.txt")
            .putMetadata("key1", "value1")
            .putMetadata("key2", "value2")
            .setExpectedSize(1024L)
            .setMimeType("text/plain")
            .setConnectorId("connector-123")
            .setPath("test/path/")
            .setFailIfExists(false)
            .build();

        String json = ProtoJson.toJson(request);

        // Verify JSON contains all fields
        assertThat("JSON should not be null", json, is(notNullValue()));
        assertThat("JSON should contain drive", json, containsString("test-drive"));
        assertThat("JSON should contain parentId", json, containsString("parent-123"));
        assertThat("JSON should contain name", json, containsString("test-file.txt"));
        assertThat("JSON should contain metadata", json, containsString("key1"));
        assertThat("JSON should contain expectedSize", json, containsString("1024"));
        assertThat("JSON should contain mimeType", json, containsString("text/plain"));
        assertThat("JSON should contain connectorId", json, containsString("connector-123"));
        assertThat("JSON should contain path", json, containsString("test/path/"));
    }

    @Test
    void testToJson_WithDefaultValues() {
        // Create a request with only required fields (default values for others)
        InitiateUploadRequest request = InitiateUploadRequest.newBuilder()
            .setDrive("test-drive")
            .setName("test-file.txt")
            .build();

        String json = ProtoJson.toJson(request);

        // Verify JSON - with includingDefaultValueFields(), it should include all fields
        assertThat("JSON should not be null", json, is(notNullValue()));
        assertThat("JSON should contain drive", json, containsString("test-drive"));
        assertThat("JSON should contain name", json, containsString("test-file.txt"));
        
        // Check if default values are included
        // failIfExists defaults to false, so it might be included
        System.out.println("JSON with defaults: " + json);
    }

    @Test
    void testToJson_CompareWithAndWithoutDefaults() {
        // Create a request with only some fields
        InitiateUploadRequest request = InitiateUploadRequest.newBuilder()
            .setDrive("test-drive")
            .setName("test-file.txt")
            .setConnectorId("connector-123")
            .build();

        // Serialize with defaults (for responses)
        String jsonWithDefaults = ProtoJson.toJson(request);

        // Serialize without defaults (for request matching - what gRPC actually sends)
        String jsonWithoutDefaults = ProtoJson.toJsonWithoutDefaults(request);

        System.out.println("With defaults: " + jsonWithDefaults);
        System.out.println("Without defaults: " + jsonWithoutDefaults);

        // They should be different if default values are included
        // This helps us understand the mismatch
        assertThat("JSON representations should exist", jsonWithDefaults, is(notNullValue()));
        assertThat("JSON representations should exist", jsonWithoutDefaults, is(notNullValue()));
        
        // JSON without defaults should be shorter (fewer fields)
        assertThat("JSON without defaults should be shorter or equal", 
            jsonWithoutDefaults.length(), is(lessThanOrEqualTo(jsonWithDefaults.length())));
    }

    @Test
    void testToJson_WithMetadataMap() {
        // Test metadata map serialization
        InitiateUploadRequest request = InitiateUploadRequest.newBuilder()
            .setDrive("test-drive")
            .setName("test-file.txt")
            .putMetadata("key1", "value1")
            .putMetadata("key2", "value2")
            .build();

        String json = ProtoJson.toJson(request);

        assertThat("JSON should contain metadata", json, containsString("metadata"));
        assertThat("JSON should contain key1", json, containsString("key1"));
        assertThat("JSON should contain value1", json, containsString("value1"));
        assertThat("JSON should contain key2", json, containsString("key2"));
        assertThat("JSON should contain value2", json, containsString("value2"));
    }

    @Test
    void testToJson_RequestMatchingScenario() {
        // Simulate the exact scenario from the failing test
        InitiateUploadRequest expectedRequest = InitiateUploadRequest.newBuilder()
            .setDrive("test-drive")
            .setName("test-file.txt")
            .setConnectorId("connector-123")
            .build();

        // Build a "fresh" request with same values (simulating what gRPC sends)
        InitiateUploadRequest actualRequest = InitiateUploadRequest.newBuilder()
            .setDrive("test-drive")
            .setName("test-file.txt")
            .setConnectorId("connector-123")
            .build();

        // Use toJsonWithoutDefaults for request matching (what WireMockGrpcCompat uses)
        String expectedJson = ProtoJson.toJsonWithoutDefaults(expectedRequest);
        String actualJson = ProtoJson.toJsonWithoutDefaults(actualRequest);

        System.out.println("Expected JSON (no defaults): " + expectedJson);
        System.out.println("Actual JSON (no defaults): " + actualJson);

        // They should be identical
        assertEquals(expectedJson, actualJson, "Both requests should serialize to same JSON without defaults");
        
        // Also test with defaults to see the difference
        String expectedWithDefaults = ProtoJson.toJson(expectedRequest);
        String actualWithDefaults = ProtoJson.toJson(actualRequest);
        System.out.println("Expected JSON (with defaults): " + expectedWithDefaults);
        System.out.println("Actual JSON (with defaults): " + actualWithDefaults);
    }

    @Test
    void testToJson_WithOptionalFields() {
        // Test with optional fields that have default values
        InitiateUploadRequest request1 = InitiateUploadRequest.newBuilder()
            .setDrive("test-drive")
            .setName("test-file.txt")
            .setFailIfExists(false)  // Explicitly set to false
            .build();

        InitiateUploadRequest request2 = InitiateUploadRequest.newBuilder()
            .setDrive("test-drive")
            .setName("test-file.txt")
            // failIfExists not set (defaults to false)
            .build();

        String json1 = ProtoJson.toJson(request1);
        String json2 = ProtoJson.toJson(request2);

        System.out.println("With explicit false: " + json1);
        System.out.println("Without explicit false: " + json2);

        // Check if they're the same or different
        // This helps identify if explicit false vs default false causes issues
        assertThat("Both JSONs should exist", json1, is(notNullValue()));
        assertThat("Both JSONs should exist", json2, is(notNullValue()));
    }
}

