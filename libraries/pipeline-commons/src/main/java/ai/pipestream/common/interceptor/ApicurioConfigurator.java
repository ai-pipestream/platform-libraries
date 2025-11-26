package ai.pipestream.common.interceptor;

import io.smallrye.common.annotation.Identifier;
import io.smallrye.reactive.messaging.ClientCustomizer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Set;

/**
 * Runtime configurator for Kafka clients in SmallRye Reactive Messaging.
 *
 * <p>This class implements the {@link ClientCustomizer} interface to customize
 * Kafka producer and consumer configurations at runtime. It enforces consistent
 * Protobuf serialization/deserialization settings across all Kafka channels
 * and ensures proper integration with the Apicurio Registry.</p>
 *
 * <h2>Purpose</h2>
 * <p>While the {@link ai.pipestream.kafka.connector.deployment.ProtobufKafkaProcessor}
 * handles build-time configuration generation, this class provides runtime
 * enforcement and fallback configuration. It acts as a safety net to ensure
 * that all Kafka channels use the correct serializers and registry settings.</p>
 *
 * <h2>Configuration Applied</h2>
 * <p>This configurator sets the following Kafka properties for all channels:</p>
 * <ul>
 *   <li><strong>Serializers/Deserializers:</strong> Forces Protobuf and UUID classes</li>
 *   <li><strong>Apicurio Registry:</strong> URL, auto-registration, and schema lookup</li>
 *   <li><strong>Return Class:</strong> Handled by build-time configuration (not here)</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe as it only reads from injected configuration
 * and modifies the provided configuration map. No mutable instance state
 * is maintained between invocations.</p>
 *
 * <h2>Logging</h2>
 * <p>Configuration changes are logged at DEBUG level, with sensitive values
 * (passwords, secrets, tokens) automatically redacted in the logs.</p>
 *
 * @see io.smallrye.reactive.messaging.ClientCustomizer
 * @see ai.pipestream.kafka.connector.deployment.ProtobufKafkaProcessor
 * @since 0.2.10
 */
@ApplicationScoped
@Identifier("smallrye-kafka")
public class ApicurioConfigurator implements ClientCustomizer<Map<String, Object>> {

    /** Logger for configuration operations and debugging. */
    private static final Logger LOG = Logger.getLogger(ApicurioConfigurator.class);

    /** Injected MicroProfile Config for accessing application configuration. */
    @Inject
    Config config;

    /**
     * Set of terms that indicate sensitive configuration values.
     *
     * <p>When logging configuration changes, values for keys containing these terms
     * are redacted as "[REDACTED]" instead of showing the actual value. Note that
     * "serializer" and "deserializer" are explicitly excluded to allow debugging
     * of serialization configuration.</p>
     */
    private static final Set<String> SENSITIVE_TERMS = Set.of("password", "secret", "token", "apikey");


    /**
     * Customizes Kafka client configuration for a specific channel.
     *
     * <p>This method is called by SmallRye Reactive Messaging for each Kafka channel
     * (both producers and consumers) to allow customization of the Kafka client properties.
     * It enforces consistent Protobuf serialization and Apicurio Registry settings across
     * all channels.</p>
     *
     * <h3>Configuration Strategy</h3>
     * <p>The method applies a "sledgehammer" approach to ensure Protobuf serialization:</p>
     * <ol>
     *   <li><b>Force Serializers:</b> Explicitly sets Protobuf and UUID classes</li>
     *   <li><b>Apicurio Settings:</b> Configures registry URL and auto-registration</li>
     *   <li><b>Return Class:</b> Handled by build-time configuration (not here)</li>
     * </ol>
     *
     * <h3>Why This Approach</h3>
     * <p>SmallRye Reactive Messaging may default to Jackson serialization if it cannot
     * determine the correct serializer. This configurator prevents that by explicitly
     * setting the Protobuf serializers, ensuring consistent message processing.</p>
     *
     * @param channelName The name of the messaging channel being configured
     * @param channelConfig The MicroProfile Config for this specific channel
     * @param kafkaConfig The mutable Kafka configuration map to customize
     * @return The modified kafkaConfig map (same instance, modified in-place)
     *
     * @see #putAndLog(Map, String, Object)
     * @see #isSensitive(String)
     */
    @Override
    public Map<String, Object> customize(String channelName, Config channelConfig, Map<String, Object> kafkaConfig) {
        LOG.debugf("🔧 [ApicurioConfigurator] Enforcing defaults for: %s", channelName);

        // 1. FORCE SERIALIZERS (The Sledgehammer)
        // This prevents SmallRye from ever guessing "Jackson"
        kafkaConfig.put("key.deserializer", "org.apache.kafka.common.serialization.UUIDDeserializer");
        kafkaConfig.put("value.deserializer", "io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer");
        kafkaConfig.put("key.serializer", "org.apache.kafka.common.serialization.UUIDSerializer");
        kafkaConfig.put("value.serializer", "io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer");

        // 2. APICURIO SETTINGS
        // The URL is already bridged by the extension, but setting it here covers edge cases
        String registryUrl = config.getOptionalValue("apicurio.registry.url", String.class)
                .orElse("http://localhost:8080/apis/registry/v3");

        putAndLog(kafkaConfig, "apicurio.registry.url", registryUrl);
        putAndLog(kafkaConfig, "apicurio.registry.auto-register", "true");
        putAndLog(kafkaConfig, "apicurio.registry.find-latest", "true");

        // Note: 'return-class' is handled by the Build Step (System Properties).
        // We rely on that injection.

        return kafkaConfig;
    }

    /**
     * Adds a configuration property and logs the change.
     *
     * <p>This method updates the Kafka configuration map and logs the change
     * at DEBUG level. Sensitive values (passwords, secrets, etc.) are automatically
     * redacted in the log output.</p>
     *
     * @param config The Kafka configuration map to modify
     * @param key The configuration property key
     * @param value The configuration property value
     *
     * @see #isSensitive(String)
     */
    private void putAndLog(Map<String, Object> config, String key, Object value) {
        config.put(key, value);
        if (isSensitive(key)) {
            LOG.debugf("  SET %s = [REDACTED]", key);
        } else {
            LOG.debugf("  SET %s = %s", key, value);
        }
    }

    /**
     * Determines if a configuration key contains sensitive information.
     *
     * <p>This method checks if the configuration key contains any of the
     * predefined sensitive terms. Keys containing "serializer" or "deserializer"
     * are explicitly excluded from being considered sensitive to allow
     * debugging of serialization configuration.</p>
     *
     * @param key The configuration property key to check
     * @return true if the key contains sensitive terms, false otherwise
     *
     * @see #SENSITIVE_TERMS
     * @see #putAndLog(Map, String, Object)
     */
    private boolean isSensitive(String key) {
        String lower = key.toLowerCase();
        // Don't redact config keys like "value.serializer" even though they contain "s-e-r-..."
        if (lower.contains("serializer") || lower.contains("deserializer")) return false;
        return SENSITIVE_TERMS.stream().anyMatch(lower::contains);
    }
}