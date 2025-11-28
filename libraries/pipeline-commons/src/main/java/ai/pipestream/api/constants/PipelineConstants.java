package ai.pipestream.api.constants;

/**
 * Common constants used across the pipeline system.
 * These constants ensure consistency and avoid hardcoded values.
 */
public final class PipelineConstants {
    
    private PipelineConstants() {
        // Utility class
    }
    
    // Application defaults
    public static final String DEFAULT_APP_NAME = "pipeline-engine";
    
    // Consul key patterns
    public static final String CONSUL_KEY_SEPARATOR = "/";
    public static final String CONSUL_CLUSTERS_KEY = "clusters";
    public static final String CONSUL_PIPELINES_KEY = "pipelines";
    public static final String CONSUL_MODULES_KEY = "modules";
    public static final String CONSUL_CONFIG_KEY = "config";
    public static final String CONSUL_DEFINITIONS_KEY = "definitions";
    public static final String CONSUL_REGISTERED_KEY = "registered";
    public static final String CONSUL_ENABLED_MODULES_KEY = "enabled-modules";
    
    // Module deployment constants
    public static final String MODULE_LABEL_PREFIX = "pipeline.";

    /**
     * Build a Consul key path with the application name prefix.
     * 
     * @param appName The application name
     * @param pathSegments The path segments to join
     * @return The full Consul key path
     */
    public static String buildConsulKey(String appName, String... pathSegments) {
        StringBuilder key = new StringBuilder(appName);
        for (String segment : pathSegments) {
            key.append(CONSUL_KEY_SEPARATOR).append(segment);
        }
        return key.toString();
    }
}