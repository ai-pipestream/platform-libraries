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
        Set<String> names = new HashSet<>();
        // Only return property names if the extension is active (enabled property is set)
        if (System.getProperty("pipeline.devservices.enabled") != null) {
            names.add(PREFIX + "enabled");
            names.add(PREFIX + "files");
            names.add(PREFIX + "project-name");
            names.add(PREFIX + "start-services");
            names.add(PREFIX + "stop-services");
            names.add(PREFIX + "reuse-project-for-tests");
        }
        return names;
    }

    @Override
    public String getValue(String propertyName) {
        // Only provide values if the extension is active (enabled property is set)
        if (System.getProperty("pipeline.devservices.enabled") == null) {
            return null;
        }

        if (!propertyName.startsWith(PREFIX)) {
            return null;
        }

        String key = propertyName.substring(PREFIX.length());
        String propKey = "pipeline.devservices." + key;
        String value = System.getProperty(propKey);
        if (value != null) {
            LOG.info(propKey + ": " + value);
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
