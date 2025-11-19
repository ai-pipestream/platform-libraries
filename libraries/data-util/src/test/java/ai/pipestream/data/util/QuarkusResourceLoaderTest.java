package ai.pipestream.data.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QuarkusResourceLoader, specifically the new methods for loading test documents.
 */
@DisplayName("QuarkusResourceLoader Test Document Loading Tests")
class QuarkusResourceLoaderTest {

    @Test
    @DisplayName("Should load a test document as bytes")
    void testLoadTestDocument() throws IOException {
        byte[] content = QuarkusResourceLoader.loadTestDocument("sample_image/sample.jpg");
        assertNotNull(content, "Document content should not be null");
        assertTrue(content.length > 0, "Document should have content");
    }

    @Test
    @DisplayName("Should load a PDF document")
    void testLoadPdfDocument() throws IOException {
        byte[] content = QuarkusResourceLoader.loadTestDocument("sample_miscellaneous_files/sample.pdf");
        assertNotNull(content, "PDF content should not be null");
        assertTrue(content.length > 0, "PDF should have content");
    }

    @Test
    @DisplayName("Should load a text document")
    void testLoadTextDocument() throws IOException {
        byte[] content = QuarkusResourceLoader.loadTestDocument("sample_text/sample.txt");
        assertNotNull(content, "Text content should not be null");
        assertTrue(content.length > 0, "Text should have content");
    }

    @Test
    @DisplayName("Should throw IOException if document not found")
    void testLoadDocumentNotFound() {
        IOException exception = assertThrows(IOException.class, () -> {
            QuarkusResourceLoader.loadTestDocument("non_existent_file.xyz");
        });
        assertTrue(exception.getMessage().contains("Document not found"), 
            "Exception message should indicate document not found");
    }

    @Test
    @DisplayName("Should list resource paths from a directory")
    void testListResourcePaths() {
        List<String> paths = QuarkusResourceLoader.listResourcePaths("sample_image");
        assertNotNull(paths, "Paths list should not be null");
        assertFalse(paths.isEmpty(), "Should find at least one file in sample_image");
        
        // Verify paths are properly formatted
        for (String path : paths) {
            assertTrue(path.startsWith("sample_image/"), 
                "Path should start with directory name: " + path);
        }
    }

    @Test
    @DisplayName("Should list resource paths from sample_text directory")
    void testListResourcePathsText() {
        List<String> paths = QuarkusResourceLoader.listResourcePaths("sample_text");
        assertNotNull(paths, "Paths list should not be null");
        assertFalse(paths.isEmpty(), "Should find at least one file in sample_text");
    }

    @Test
    @DisplayName("Should return empty list for non-existent directory")
    void testListResourcePathsNonExistent() {
        List<String> paths = QuarkusResourceLoader.listResourcePaths("non_existent_directory");
        assertNotNull(paths, "Paths list should not be null");
        assertTrue(paths.isEmpty(), "Should return empty list for non-existent directory");
    }

    @Test
    @DisplayName("Should load documents from listed paths")
    void testLoadDocumentsFromListedPaths() throws IOException {
        List<String> paths = QuarkusResourceLoader.listResourcePaths("sample_image");
        assertFalse(paths.isEmpty(), "Should find files");
        
        // Load the first document from the list
        String firstPath = paths.get(0);
        byte[] content = QuarkusResourceLoader.loadTestDocument(firstPath);
        assertNotNull(content, "Should load document from listed path");
        assertTrue(content.length > 0, "Document should have content");
    }

    @Test
    @DisplayName("Should handle path with leading slash")
    void testLoadDocumentWithLeadingSlash() throws IOException {
        byte[] content = QuarkusResourceLoader.loadTestDocument("/sample_image/sample.jpg");
        assertNotNull(content, "Should handle leading slash");
        assertTrue(content.length > 0, "Document should have content");
    }
}

