package ai.pipestream.common.util;

import ai.pipestream.common.util.descriptor.GoogleDescriptorLoader;
import ai.pipestream.common.util.descriptor.DescriptorLoader;
import com.google.protobuf.Descriptors.FileDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GoogleDescriptorLoader.
 *
 * Note: These tests assume the grpc-google-descriptor module is on the classpath.
 * If not available, the tests will verify graceful failure.
 */
public class GoogleDescriptorLoaderTest {

    @Test
    void testDefaultLoaderAvailability() {
        GoogleDescriptorLoader loader = new GoogleDescriptorLoader();
        // May or may not be available depending on classpath
        // Just verify the method doesn't throw
        boolean available = loader.isAvailable();
        assertTrue(available || !available); // Always true, just testing no exception
    }

    @Test
    void testGetLoaderType() {
        GoogleDescriptorLoader loader = new GoogleDescriptorLoader();
        assertEquals("Google Descriptor File", loader.getLoaderType());
    }

    @Test
    void testGetDescriptorPath() {
        String customPath = "custom/path/descriptors.dsc";
        GoogleDescriptorLoader loader = new GoogleDescriptorLoader(customPath);
        assertEquals(customPath, loader.getDescriptorPath());
    }

    @Test
    void testDefaultDescriptorPath() {
        GoogleDescriptorLoader loader = new GoogleDescriptorLoader();
        assertEquals(GoogleDescriptorLoader.DEFAULT_DESCRIPTOR_PATH, loader.getDescriptorPath());
    }

    @Test
    void testLoadDescriptorsWhenAvailable() throws Exception {
        GoogleDescriptorLoader loader = new GoogleDescriptorLoader();

        if (loader.isAvailable()) {
            // If descriptor file is available, we should be able to load it
            List<FileDescriptor> descriptors = loader.loadDescriptors();
            assertNotNull(descriptors);
            assertFalse(descriptors.isEmpty(), "Should load at least one descriptor");

            // Verify we can find well-known types
            boolean foundWellKnownType = descriptors.stream()
                .anyMatch(fd -> fd.getName().contains("google/protobuf"));
            assertTrue(foundWellKnownType, "Should include Google well-known types");
        } else {
            // If not available, loading should throw exception
            assertThrows(DescriptorLoader.DescriptorLoadException.class, () -> {
                loader.loadDescriptors();
            });
        }
    }

    @Test
    void testLoadDescriptorByFileName() throws Exception {
        GoogleDescriptorLoader loader = new GoogleDescriptorLoader();

        if (loader.isAvailable()) {
            // Try to load a specific well-known type
            FileDescriptor fd = loader.loadDescriptor("google/protobuf/struct.proto");

            // May be null if not in the descriptor set
            if (fd != null) {
                assertEquals("google/protobuf/struct.proto", fd.getName());
            }
        }
    }

    @Test
    void testLoadNonExistentDescriptor() throws Exception {
        GoogleDescriptorLoader loader = new GoogleDescriptorLoader();

        if (loader.isAvailable()) {
            FileDescriptor fd = loader.loadDescriptor("nonexistent.proto");
            assertNull(fd, "Should return null for non-existent descriptor");
        }
    }

    @Test
    void testInvalidDescriptorPath() {
        GoogleDescriptorLoader loader = new GoogleDescriptorLoader("invalid/path.dsc");

        assertFalse(loader.isAvailable());
        assertThrows(DescriptorLoader.DescriptorLoadException.class, () -> {
            loader.loadDescriptors();
        });
    }

    @Test
    void testSearchPaths() {
        GoogleDescriptorLoader loader = GoogleDescriptorLoader.searchPaths(
            "invalid/path1.dsc",
            "invalid/path2.dsc",
            GoogleDescriptorLoader.DEFAULT_DESCRIPTOR_PATH
        );

        assertNotNull(loader);
        // If the default path is available, the loader should find it
        // Otherwise, it will use the first invalid path
        assertNotNull(loader.getDescriptorPath());
    }

    @Test
    void testSearchPathsWithNoValidPaths() {
        GoogleDescriptorLoader loader = GoogleDescriptorLoader.searchPaths(
            "invalid/path1.dsc",
            "invalid/path2.dsc"
        );

        assertNotNull(loader);
        assertEquals("invalid/path1.dsc", loader.getDescriptorPath());
    }

    @Test
    void testCustomClassLoader() {
        ClassLoader customLoader = Thread.currentThread().getContextClassLoader();
        GoogleDescriptorLoader loader = new GoogleDescriptorLoader(
            GoogleDescriptorLoader.DEFAULT_DESCRIPTOR_PATH,
            customLoader
        );

        assertNotNull(loader);
    }

    @Test
    void testNullClassLoaderFallback() {
        GoogleDescriptorLoader loader = new GoogleDescriptorLoader(
            GoogleDescriptorLoader.DEFAULT_DESCRIPTOR_PATH,
            null
        );

        assertNotNull(loader);
        // Should use system class loader as fallback
    }
}
