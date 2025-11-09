package ai.pipestream.quarkus.devservices.runtime;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Lazy ConfigSource that reads BUILD_TIME configuration for Quarkus Compose Dev Services
 * from static variables set by the deployment module build step.
 * This allows the values to be read when BUILD_TIME config is accessed during build steps,
 * not when the ConfigBuilder is invoked.
 */
public class PipelineDevServicesConfigSource implements ConfigSource {

    private static final Logger LOG = Logger.getLogger(PipelineDevServicesConfigSource.class);
    private static final String PREFIX = "quarkus.compose.devservices.";

    @Override
    public Set<String> getPropertyNames() {
        LOG.info("========================================");
        LOG.info("PipelineDevServicesConfigSource.getPropertyNames() called");
        LOG.info("  enabled: " + System.getProperty("pipeline.devservices.enabled"));
        LOG.info("========================================");
        Set<String> names = new HashSet<>();
        // Only return property names if the extension is active (enabled property is set)
        if (System.getProperty("pipeline.devservices.enabled") != null) {
            names.add(PREFIX + "enabled");
            names.add(PREFIX + "files");
            names.add(PREFIX + "project-name");
            names.add(PREFIX + "start-services");
            names.add(PREFIX + "stop-services");
            names.add(PREFIX + "reuse-project-for-tests");
            LOG.info("  Returning " + names.size() + " property names");
        } else {
            LOG.debug("  enabled property not set - returning empty set");
        }
        return names;
    }

    @Override
    public String getValue(String propertyName) {
        LOG.info("========================================");
        LOG.info("PipelineDevServicesConfigSource.getValue() called");
        LOG.info("  propertyName: " + propertyName);
        LOG.info("========================================");

        // Only provide values if the extension is active (enabled property is set)
        if (System.getProperty("pipeline.devservices.enabled") == null) {
            LOG.debug("  enabled property not set - returning null");
            return null;
        }

        if (!propertyName.startsWith(PREFIX)) {
            LOG.debug("  propertyName does not start with prefix - returning null");
            return null;
        }

        String key = propertyName.substring(PREFIX.length());
        String propKey = "pipeline.devservices." + key;
        String value = System.getProperty(propKey);
        if (value != null) {
            LOG.info("  Returning value for " + propKey + ": " + value);
        } else {
            LOG.debug("  No value found for " + propKey);
        }
        return value;
    }

    @Override
    public String getName() {
        return "PipelineDevServicesConfigSource";
    }

    @Override
    public int getOrdinal() {
        // Use ordinal 250 to ensure it takes precedence over default config sources
        // but can still be overridden by application.properties
        return 250;
    }
}
