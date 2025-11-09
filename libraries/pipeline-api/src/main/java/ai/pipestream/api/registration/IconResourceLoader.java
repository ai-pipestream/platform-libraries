package ai.pipestream.api.registration;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Utility for loading SVG icons from various sources and converting them to base64 data URIs.
 * Supports classpath resources, file system paths, URLs, and inline base64 data.
 */
@ApplicationScoped
public class IconResourceLoader {
    
    private static final Logger LOG = Logger.getLogger(IconResourceLoader.class);
    private static final String SVG_DATA_URI_PREFIX = "data:image/svg+xml;base64,";
    private static final int MAX_ICON_SIZE_BYTES = 64 * 1024; // 64KB max for SVG icons
    
    private final HttpClient httpClient;
    
    public IconResourceLoader() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    /**
     * Load an SVG icon from the specified path and return as base64 data URI.
     * 
     * @param iconPath Path to the icon. Supports:
     *                 - classpath:/icons/service.svg (bundled resource)
     *                 - /absolute/path/to/icon.svg (file system)
     *                 - https://cdn.example.com/icon.svg (URL)
     *                 - data:image/svg+xml;base64,... (already encoded)
     * @return Base64 data URI or empty if loading fails
     */
    public Optional<String> loadIcon(String iconPath) {
        if (iconPath == null || iconPath.trim().isEmpty()) {
            return Optional.empty();
        }
        
        String trimmedPath = iconPath.trim();
        
        try {
            if (trimmedPath.startsWith("data:image/svg+xml;base64,")) {
                // Already a base64 data URI
                return validateAndReturnDataUri(trimmedPath);
            } else if (trimmedPath.startsWith("classpath:")) {
                return loadFromClasspath(trimmedPath.substring("classpath:".length()));
            } else if (trimmedPath.startsWith("http://") || trimmedPath.startsWith("https://")) {
                return loadFromUrl(trimmedPath);
            } else {
                return loadFromFileSystem(trimmedPath);
            }
        } catch (Exception e) {
            LOG.warnf("Failed to load icon from path '%s': %s", iconPath, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Load SVG from classpath resource
     */
    private Optional<String> loadFromClasspath(String resourcePath) {
        // Remove leading slash if present
        String cleanPath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(cleanPath)) {
            if (inputStream == null) {
                LOG.warnf("Classpath resource not found: %s", cleanPath);
                return Optional.empty();
            }
            
            byte[] svgBytes = inputStream.readAllBytes();
            return validateAndConvertToDataUri(svgBytes, "classpath:" + cleanPath);
            
        } catch (IOException e) {
            LOG.warnf("Error reading classpath resource '%s': %s", cleanPath, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Load SVG from file system
     */
    private Optional<String> loadFromFileSystem(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                LOG.warnf("File not found: %s", filePath);
                return Optional.empty();
            }
            
            byte[] svgBytes = Files.readAllBytes(path);
            return validateAndConvertToDataUri(svgBytes, filePath);
            
        } catch (IOException e) {
            LOG.warnf("Error reading file '%s': %s", filePath, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Load SVG from URL
     */
    private Optional<String> loadFromUrl(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
            
            HttpResponse<byte[]> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofByteArray());
            
            if (response.statusCode() != 200) {
                LOG.warnf("HTTP error loading icon from URL '%s': %d", url, response.statusCode());
                return Optional.empty();
            }
            
            return validateAndConvertToDataUri(response.body(), url);
            
        } catch (Exception e) {
            LOG.warnf("Error loading icon from URL '%s': %s", url, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Validate and convert SVG bytes to base64 data URI
     */
    private Optional<String> validateAndConvertToDataUri(byte[] svgBytes, String source) {
        if (svgBytes.length == 0) {
            LOG.warnf("Empty SVG file from source: %s", source);
            return Optional.empty();
        }
        
        if (svgBytes.length > MAX_ICON_SIZE_BYTES) {
            LOG.warnf("SVG file too large (%d bytes, max %d) from source: %s", 
                     svgBytes.length, MAX_ICON_SIZE_BYTES, source);
            return Optional.empty();
        }
        
        // Basic SVG validation - check if it starts with SVG content
        String svgContent = new String(svgBytes);
        if (!isValidSvg(svgContent)) {
            LOG.warnf("Invalid SVG content from source: %s", source);
            return Optional.empty();
        }
        
        // Convert to base64 data URI
        String base64 = Base64.getEncoder().encodeToString(svgBytes);
        String dataUri = SVG_DATA_URI_PREFIX + base64;
        
        LOG.debugf("Successfully loaded SVG icon (%d bytes) from: %s", svgBytes.length, source);
        return Optional.of(dataUri);
    }
    
    /**
     * Validate that an existing data URI is properly formatted
     */
    private Optional<String> validateAndReturnDataUri(String dataUri) {
        try {
            // Extract and validate the base64 part
            String base64Part = dataUri.substring(SVG_DATA_URI_PREFIX.length());
            byte[] decodedBytes = Base64.getDecoder().decode(base64Part);
            
            if (decodedBytes.length > MAX_ICON_SIZE_BYTES) {
                LOG.warnf("Inline SVG data URI too large: %d bytes", decodedBytes.length);
                return Optional.empty();
            }
            
            String svgContent = new String(decodedBytes);
            if (!isValidSvg(svgContent)) {
                LOG.warn("Invalid SVG content in data URI");
                return Optional.empty();
            }
            
            return Optional.of(dataUri);
            
        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid base64 data in SVG data URI: %s", e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Basic SVG validation - checks for SVG element and basic structure
     */
    private boolean isValidSvg(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = content.trim();
        
        // Must contain <svg> element
        if (!trimmed.contains("<svg")) {
            return false;
        }
        
        // Basic XML structure check
        if (!trimmed.startsWith("<") || !trimmed.endsWith(">")) {
            return false;
        }
        
        // Check for potential security issues (basic XSS prevention)
        String lowerContent = trimmed.toLowerCase();
        if (lowerContent.contains("<script") || 
            lowerContent.contains("javascript:") || 
            lowerContent.contains("on" + "click") ||  // Split to avoid false positive
            lowerContent.contains("on" + "load")) {
            LOG.warn("SVG contains potentially unsafe content");
            return false;
        }
        
        return true;
    }
    
    /**
     * Get a default icon as base64 data URI for services without custom icons
     */
    public String getDefaultServiceIcon() {
        // Simple default SVG icon - a gear/cog symbol
        String defaultSvg = """
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 15.5A3.5 3.5 0 0 1 8.5 12A3.5 3.5 0 0 1 12 8.5a3.5 3.5 0 0 1 3.5 3.5a3.5 3.5 0 0 1-3.5 3.5m7.43-2.53c.04-.32.07-.64.07-.97c0-.33-.03-.66-.07-1l2.11-1.63c.19-.15.24-.42.12-.64l-2-3.46c-.12-.22-.39-.31-.61-.22l-2.49 1c-.52-.39-1.06-.73-1.69-.98l-.37-2.65A.506.506 0 0 0 14 2h-4c-.25 0-.46.18-.5.42l-.37 2.65c-.63.25-1.17.59-1.69.98l-2.49-1c-.22-.09-.49 0-.61.22l-2 3.46c-.13.22-.07.49.12.64L4.57 11c-.04.34-.07.67-.07 1c0 .33.03.65.07.97l-2.11 1.66c-.19.15-.25.42-.12.64l2 3.46c.12.22.39.3.61.22l2.49-1.01c.52.4 1.06.74 1.69.99l.37 2.65c.04.24.25.42.5.42h4c.25 0 .46-.18.5-.42l.37-2.65c.63-.26 1.17-.59 1.69-.99l2.49 1.01c.22.08.49 0 .61-.22l2-3.46c.12-.22.07-.49-.12-.64l-2.11-1.66Z" fill="currentColor"/>
            </svg>
            """;
        
        byte[] svgBytes = defaultSvg.getBytes();
        String base64 = Base64.getEncoder().encodeToString(svgBytes);
        return SVG_DATA_URI_PREFIX + base64;
    }
}
