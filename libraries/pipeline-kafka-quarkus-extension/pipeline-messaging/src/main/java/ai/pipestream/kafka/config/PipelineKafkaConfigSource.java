package ai.pipestream.kafka.config;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;

import java.util.*;

/**
 * Provides default Kafka configuration for the Pipeline application.
 * <p>
 * <b>Purpose:</b> This config source supplies sensible defaults for Kafka producer and consumer settings,
 * including serializer/deserializer classes, performance, and reliability options. It is intended to
 * enable "zero-config" operation for most pipeline deployments.
 * </p>
 * <p>
 * <b>Configuration Priority:</b> This config source has an ordinal of 300, giving it high priority
 * over most other config sources except for explicit application or environment overrides.
 * </p>
 * <p>
 * <b>Environment Variable Naming:</b> Topic-specific configuration can be provided via environment variables
 * following the naming convention <code>PIPELINE_TOPIC_{TOPIC_NAME}</code>, where <code>{TOPIC_NAME}</code>
 * is the uppercase topic name. For example, <code>PIPELINE_TOPIC_EVENTS</code> configures the "events" topic.
 * </p>
 * <p>
 * <b>Direction Heuristic:</b> The direction (input/output) for a topic is determined heuristically based on
 * the context or value of the environment variable. For example, if the variable value contains "in" or "out",
 * or based on the application's topic usage patterns, the config source will infer whether the topic is used
 * for consumption or production.
 * </p>
 */
public class PipelineKafkaConfigSource implements ConfigSource {
    private static final Logger LOG = Logger.getLogger(PipelineKafkaConfigSource.class);
    
    private static final String PIPELINE_TOPIC_PREFIX = "PIPELINE_TOPIC_";
    private final Map<String, String> config = new HashMap<>();
    private final Map<String, String> env;

    /**
     * Creates a config source using the current process environment variables for overrides.
     * <p>
     * This no-arg constructor is used by MicroProfile Config at runtime. It delegates to
     * {@link #PipelineKafkaConfigSource(Map)} with {@link System#getenv()} to enable environment-based
     * configuration overrides in addition to built-in defaults.
     * </p>
     */
    public PipelineKafkaConfigSource() {
        this(System.getenv());
    }

    // Visible for testing
    /**
     * Creates a config source with a provided environment map.
     * <p>
     * Primarily intended for tests, this constructor allows injecting a synthetic environment map so
     * tests can control and verify override behavior deterministically without relying on the real
     * process environment.
     * </p>
     *
     * @param env map of environment variables to consult for overrides; keys are expected in upper-case
     *            underscore form (e.g., MP_MESSAGING_CONNECTOR_SMALLRYE_KAFKA_LINGER_MS)
     */
    public PipelineKafkaConfigSource(Map<String, String> env) {
        this.env = Collections.unmodifiableMap(new HashMap<>(env));

        // 1. Global Defaults (UUID keys/values, Protobuf values, Apicurio v3 registry)
        // Serializer/Deserializer ensure consistent binary formats and schema evolution
        // across services. UUIDs for keys provide uniqueness and good partitioning.
        putConfig(
                "mp.messaging.connector.smallrye-kafka.key.serializer",
                "org.apache.kafka.common.serialization.UUIDSerializer",
                "Use UUID keys for strong uniqueness and even partition distribution."
        );
        putConfig(
                "mp.messaging.connector.smallrye-kafka.value.serializer",
                "io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer",
                "Serialize values as Protobuf leveraging Apicurio for schema management."
        );
        putConfig(
                "mp.messaging.connector.smallrye-kafka.key.deserializer",
                "org.apache.kafka.common.serialization.UUIDDeserializer",
                "Deserialize UUID keys to match the serializer for inbound messages."
        );
        putConfig(
                "mp.messaging.connector.smallrye-kafka.value.deserializer",
                "io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer",
                "Deserialize Protobuf payloads from the Apicurio registry."
        );

        // PRODUCER PERFORMANCE & RELIABILITY
        // Linger and batch size allow message batching for throughput; compression reduces network IO.
        // Idempotence + acks=all ensure exactly-once behavior per partition and durability.
        putConfig("mp.messaging.connector.smallrye-kafka.linger.ms", "20",
                "Delay sends slightly to batch records for better throughput.");
        putConfig("mp.messaging.connector.smallrye-kafka.batch.size", "65536",
                "Batch up to 64KB before sending to improve throughput.");
        putConfig("mp.messaging.connector.smallrye-kafka.compression.type", "snappy",
                "Compress records to reduce bandwidth without high CPU cost.");
        putConfig("mp.messaging.connector.smallrye-kafka.enable.idempotence", "true",
                "Enable idempotent producer to avoid duplicates on retries.");
        putConfig("mp.messaging.connector.smallrye-kafka.acks", "all",
                "Require all replicas to ack for strongest durability.");
        putConfig("mp.messaging.connector.smallrye-kafka.max.request.size", "8388608",
                "Allow up to 8MB requests to support larger messages.");

        // CONSUMER RELIABILITY
        // Start from earliest to avoid missing messages in new groups; manual commits for precise control.
        putConfig("mp.messaging.connector.smallrye-kafka.auto.offset.reset", "earliest",
                "Start from beginning if no committed offset exists.");
        putConfig("mp.messaging.connector.smallrye-kafka.enable.auto.commit", "false",
                "Disable auto-commit to coordinate offset commits with processing."
        );

        // APICURIO CONFIGURATION
        // Resolve schemas by topic, auto-register for developer convenience, and use PROTOBUF type.
        putConfig("mp.messaging.connector.smallrye-kafka.apicurio.registry.artifact-resolver-strategy",
                "io.apicurio.registry.serde.strategy.TopicIdStrategy",
                "Resolve schema artifacts by topic name to simplify lookups.");
        putConfig("mp.messaging.connector.smallrye-kafka.apicurio.registry.auto-register", "true",
                "Auto-register schemas in non-prod/dev environments for convenience.");
        putConfig("mp.messaging.connector.smallrye-kafka.apicurio.registry.artifact-type", "PROTOBUF",
                "Use Protobuf for compact, strongly-typed payloads.");
        putConfig("mp.messaging.connector.smallrye-kafka.apicurio.registry.serde.find-latest", "true",
                "Resolve to the latest compatible schema for forward evolution.");

        // APICURIO REGISTRY URL (Env Var -> Sys Prop -> Default-none)
        // Only set if explicitly provided, so platform defaults or other sources can supply it.
        putOptionalConfig(
                "mp.messaging.connector.smallrye-kafka.apicurio.registry.url",
                null,
                List.of("APICURIO_REGISTRY_URL"),
                "If provided, directs clients to the Apicurio registry endpoint."
        );

        // 2. Dynamic Topic Mapping from Env Vars AND System Properties (for testing)
        // Example: PIPELINE_TOPIC_INGEST_FILE -> mp.messaging.outgoing.ingest-file.topic
        Map<String, String> allProps = new HashMap<>(this.env);
        System.getProperties().forEach((k, v) -> allProps.put(k.toString(), v.toString()));

        allProps.forEach((key, value) -> {
            if (key.startsWith(PIPELINE_TOPIC_PREFIX)) {
                String channel = key.substring(PIPELINE_TOPIC_PREFIX.length()).toLowerCase().replace("_", "-");
                String direction;

                // Explicit direction in env var (e.g. PIPELINE_TOPIC_IN_MY_CHANNEL)
                if (key.contains("_IN_")) {
                    direction = "incoming";
                    // Remove 'in-' prefix from channel name if present due to replace above
                    channel = channel.replace("in-", "");
                } else if (key.contains("_OUT_")) {
                    direction = "outgoing";
                    channel = channel.replace("out-", "");
                } else {
                    // Fallback Heuristic: If channel has 'in' or 'consumer', it's incoming.
                    direction = (channel.contains("in") || channel.contains("consumer")) ? "incoming" : "outgoing";
                }

                String topicKey = "mp.messaging." + direction + "." + channel + ".topic";
                config.put(topicKey, value);
                LOG.debugf("Kafka config set: %s = %s (source: %s) - %s", topicKey, redact(topicKey, value),
                        key.startsWith("PIPELINE_TOPIC_") ? "env/sys PIPELINE_TOPIC_*" : "env/sys",
                        "Map logical channel to physical Kafka topic");

                String connectorKey = "mp.messaging." + direction + "." + channel + ".connector";
                config.put(connectorKey, "smallrye-kafka");
                LOG.debugf("Kafka config set: %s = %s (source: %s) - %s", connectorKey, "smallrye-kafka", "default",
                        "Ensure SmallRye Kafka connector is used for the channel");
            }
        });

        LOG.infof("PipelineKafkaConfigSource initialized with %d Kafka-related properties.", config.size());
    }

    /**
     * Adds a configuration property with support for overrides via System properties and environment variables.
     * Precedence: System property > Environment variable > Default value.
     * Logs the final value and source using JBoss-style logging.
     */
    private void putConfig(String key, String defaultValue, String reason) {
        Resolved resolved = resolveWithSource(key, defaultValue);
        config.put(key, resolved.value);
        LOG.debugf("Kafka config set: %s = %s (source: %s) - %s",
                key, redact(key, resolved.value), resolved.source, reason);
    }

    /**
     * Adds a configuration property only if an override exists (system property or environment variable).
     * Useful for optional settings that should not force a default.
     */
    private void putOptionalConfig(String key, String defaultValue, List<String> extraEnvNames, String reason) {
        Resolved resolved = resolveWithSource(key, defaultValue, extraEnvNames);
        if (!isBlank(resolved.value)) {
            config.put(key, resolved.value);
            LOG.debugf("Kafka config set: %s = %s (source: %s) - %s",
                    key, redact(key, resolved.value), resolved.source, reason);
        } else {
            LOG.debugf("Kafka config skipped: %s - %s (no value provided)", key, reason);
        }
    }

    private Resolved resolveWithSource(String key, String defaultValue) {
        return resolveWithSource(key, defaultValue, Collections.emptyList());
    }

    private Resolved resolveWithSource(String key, String defaultValue, List<String> extraEnvNames) {
        // 1) System property (exact key, with dots allowed)
        String sysVal = System.getProperty(key);
        if (!isBlank(sysVal)) {
            return new Resolved(sysVal, "sysprop:" + key);
        }

        // 2) Environment variable (MAPPED NAME) from provided env map (testable)
        String mappedEnvKey = toEnvKey(key);
        String envVal = env.get(mappedEnvKey);
        if (isBlank(envVal)) {
            envVal = System.getenv(mappedEnvKey); // fall back to real env for safety
        }
        if (!isBlank(envVal)) {
            return new Resolved(envVal, "env:" + mappedEnvKey);
        }

        // 2b) Extra env aliases (e.g., APICURIO_REGISTRY_URL)
        for (String alias : extraEnvNames) {
            String aliasVal = env.getOrDefault(alias, System.getenv(alias));
            if (!isBlank(aliasVal)) {
                return new Resolved(aliasVal, "env:" + alias);
            }
        }

        // 3) Default
        return new Resolved(defaultValue, "default");
    }

    private String toEnvKey(String key) {
        return key.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String redact(String key, String value) {
        if (value == null) return null;
        if (isSensitive(key)) return "<redacted>";
        return value;
    }

    private boolean isSensitive(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return k.contains("password") || k.contains("secret") || k.contains("token") || k.contains("sasl")
                || k.contains("credential") || k.contains("api.key") || k.contains("apikey");
    }

    private static class Resolved {
        final String value;
        final String source;
        Resolved(String value, String source) {
            this.value = value;
            this.source = source;
        }
    }

    @Override
    /**
     * Returns all properties contributed by this config source after applying overrides.
     *
     * @return immutable view of Kafka-related configuration entries supplied by this source
     */
    public Map<String, String> getProperties() {
        return config;
    }

    @Override
    /**
     * Returns the names of all properties available from this config source.
     *
     * @return set of property names provided by this source
     */
    public Set<String> getPropertyNames() {
        return config.keySet();
    }

    @Override
    /**
     * Looks up a single property value by name.
     *
     * @param propertyName the configuration key to look up
     * @return the configured value, or {@code null} if this source does not contain the key
     */
    public String getValue(String propertyName) {
        return config.get(propertyName);
    }

    @Override
    /**
     * The logical name of this config source, used in logs and diagnostics.
     *
     * @return the name identifying this source
     */
    public String getName() {
        return "PipelineZeroConfig";
    }

    @Override
    /**
     * The priority of this configuration source.
     * Higher values take precedence over lower values.
     *
     * @return 300 to ensure these defaults override low-priority sources while still allowing
     *         application and environment-specific configuration to win
     */
    public int getOrdinal() {
        return 300;
    } // High priority
}
