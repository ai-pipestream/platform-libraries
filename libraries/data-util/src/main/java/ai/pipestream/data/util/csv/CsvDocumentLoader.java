package ai.pipestream.data.util.csv;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Simple CSV document loader utility for loading document metadata.
 * Can be reused by any module that needs to load CSV-based document metadata.
 */
@Singleton
public class CsvDocumentLoader {
    
    /**
     * Creates a new CsvDocumentLoader.
     * This explicit constructor is provided primarily for dependency injection frameworks
     * and to document the default construction behavior.
     */
    public CsvDocumentLoader() {
        // default constructor for CDI / DI frameworks
    }
    
    private static final Logger LOG = Logger.getLogger(CsvDocumentLoader.class);
    
    /**
     * Loads CSV data from a resource path and returns the parsed rows.
     * First row is assumed to be headers and is skipped.
     * 
     * @param resourcePath Path to the CSV resource (e.g., "/demo-documents/documents.csv")
     * @return List of string arrays representing CSV rows (headers excluded)
     */
    public List<String[]> loadCsvData(String resourcePath) {
        List<String[]> records = new ArrayList<>();
        
        try (InputStream csvStream = getClass().getResourceAsStream(resourcePath)) {
            if (csvStream == null) {
                LOG.warnf("CSV file not found at: %s", resourcePath);
                return records;
            }
            
            try (CSVReader reader = new CSVReader(new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {
                List<String[]> allRecords = reader.readAll();
                
                if (allRecords.isEmpty()) {
                    LOG.warn("CSV file is empty");
                    return records;
                }
                
                // Skip header row, return the rest
                for (int i = 1; i < allRecords.size(); i++) {
                    records.add(allRecords.get(i));
                }
                
                LOG.infof("Loaded %d records from CSV: %s", records.size(), resourcePath);
                
            } catch (IOException | CsvException e) {
                LOG.errorf("Error reading CSV file %s: %s", resourcePath, e.getMessage());
            }
            
        } catch (IOException e) {
            LOG.errorf("Error opening CSV file %s: %s", resourcePath, e.getMessage());
        }
        
        return records;
    }
    
    /**
     * Loads CSV headers from a resource path.
     * 
     * @param resourcePath Path to the CSV resource
     * @return Optional containing the header row, or empty if not found
     */
    public Optional<String[]> loadCsvHeaders(String resourcePath) {
        try (InputStream csvStream = getClass().getResourceAsStream(resourcePath)) {
            if (csvStream == null) {
                LOG.warnf("CSV file not found at: %s", resourcePath);
                return Optional.empty();
            }
            
            try (CSVReader reader = new CSVReader(new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {
                String[] headers = reader.readNext();
                return headers != null ? Optional.of(headers) : Optional.empty();
                
            } catch (IOException | CsvException e) {
                LOG.errorf("Error reading CSV headers from %s: %s", resourcePath, e.getMessage());
                return Optional.empty();
            }
            
        } catch (IOException e) {
            LOG.errorf("Error opening CSV file %s: %s", resourcePath, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Safely parses an integer from a CSV field.
     * 
     * @param value The string value to parse
     * @return Parsed integer or null if invalid
     */
    public Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOG.warnf("Invalid integer value in CSV: '%s'", value);
            return null;
        }
    }
    
    /**
     * Safely trims and cleans CSV field values.
     * 
     * @param value The raw CSV field value
     * @return Cleaned string or null if empty
     */
    public String cleanField(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        // Remove quotes if the field is quoted
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() > 1) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.isEmpty() ? null : cleaned;
    }
}