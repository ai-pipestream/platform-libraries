# Pipeline Protobuf Kafka Connector

**Zero-config Quarkus extension for Kafka messaging with Protobuf and Apicurio Schema Registry.**

This extension automatically configures Kafka channels for Protobuf message serialization/deserialization, eliminating the need for manual Kafka configuration in your Quarkus applications.

## Features

- ✅ **Zero Configuration** - Just add dependency and use annotations
- ✅ **Automatic Protobuf Serialization** - Handles all serializers/deserializers automatically
- ✅ **Apicurio Schema Registry Integration** - Automatic schema management and evolution
- ✅ **Platform Standards** - Enforces UUID keys, reliability settings, and best practices
- ✅ **Both Emitter Types Supported** - Works with `MutinyEmitter` and regular `Emitter`
- ✅ **Build-Time Processing** - All configuration generated at build time for optimal performance
- ✅ **Automatic Classloading** - Handles all gRPC and Protobuf classloading requirements

## Quick Start

### 1. Add Dependency

```groovy
dependencies {
    implementation platform('ai.pipestream:pipeline-bom:0.2.11-SNAPSHOT') // or later
    implementation 'ai.pipestream:pipeline-protobuf-kafka-connector:0.2.11-SNAPSHOT'
    implementation 'ai.pipestream:grpc-stubs' // Your Protobuf message types
}
```

### 2. Configure Infrastructure URLs

In `application.properties`:

```properties
# REQUIRED: Infrastructure URLs (same for all environments)
kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS}
apicurio.registry.url=${APICURIO_REGISTRY_URL}
```

**Note:** The extension automatically bridges `apicurio.registry.url` to `mp.messaging.connector.smallrye-kafka.apicurio.registry.url`, so you only need to set it once.

### 3. Use in Your Code

**Producer:**
```java
import ai.pipestream.api.annotation.ProtobufChannel;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;

@ApplicationScoped
public class EventPublisher {
    @Inject
    @Channel("user-events-producer")
    @ProtobufChannel("user-events-producer")
    MutinyEmitter<UserEvent> emitter;
    
    public void publishEvent(UserEvent event) {
        emitter.sendAndAwait(event);
    }
}
```

**Consumer:**
```java
import ai.pipestream.api.annotation.ProtobufIncoming;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import java.util.UUID;

@ApplicationScoped
public class EventConsumer {
    @Incoming("user-events-consumer")
    @ProtobufIncoming("user-events-consumer")
    public void consume(ConsumerRecord<UUID, UserEvent> record) {
        // Automatic UUID key + Protobuf value deserialization
        UserEvent event = record.value();
        processEvent(event);
    }
}
```

**That's it!** No serializers, deserializers, connector configuration, or topic mapping needed.

## How It Works

### Build-Time Processing

The extension's deployment module (`ProtobufKafkaProcessor`) scans your code at build time to:

1. **Discover Channels** - Finds all `@ProtobufChannel` and `@ProtobufIncoming` annotations
2. **Extract Types** - Determines Protobuf message types from field generics and method parameters
3. **Generate Configuration** - Creates all required SmallRye Reactive Messaging configuration
4. **Configure Apicurio** - Sets up schema registry URLs and serializers/deserializers

### Automatic Topic Mapping

Channel names are automatically mapped to topic names:
- `"user-events-producer"` → topic `"user-events"`
- `"user-events-consumer"` → topic `"user-events"`

The extension removes directional suffixes (`-producer`, `-consumer`, `-in`, `-out`) to derive topic names.

### Runtime Behavior

At runtime, the extension:
- Registers `ApicurioConfigurator` as a CDI bean
- Bridges `apicurio.registry.url` to SmallRye configuration
- Uses parent-first classloading for gRPC, Protobuf, and related dependencies

## Annotations

### @ProtobufChannel

Marks a `@Channel`-injected emitter/producer for automatic Protobuf configuration.

```java
@Channel("my-channel")
@ProtobufChannel("my-channel")
MutinyEmitter<MyEvent> emitter;
```

**Optional Properties:**
```java
@ProtobufChannel(
    value = "my-channel",
    properties = {"topic=custom-topic-name"}  // Override default topic mapping
)
```

### @ProtobufIncoming

Marks an `@Incoming`-annotated consumer method for automatic Protobuf configuration.

```java
@Incoming("my-channel")
@ProtobufIncoming("my-channel")
public void consume(ConsumerRecord<UUID, MyEvent> record) {
    // ...
}
```

**Required:** The method parameter must be `ConsumerRecord<UUID, YourProtobufType>`.

**Optional Properties:**
```java
@ProtobufIncoming(
    value = "my-channel",
    properties = {
        "topic=custom-topic-name",
        "auto.offset.reset=earliest"
    }
)
```

## Configuration

### Required Configuration

```properties
# Kafka bootstrap servers
kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS}

# Apicurio Registry URL (automatically bridged to SmallRye config)
apicurio.registry.url=${APICURIO_REGISTRY_URL}
```

### Optional Configuration

```properties
# Custom topic mapping (if needed)
mp.messaging.outgoing.my-channel.topic=custom-topic-name
mp.messaging.incoming.my-channel.topic=custom-topic-name

# Dev mode: Apicurio Registry URL with default
%dev.apicurio.registry.url=${APICURIO_REGISTRY_URL:http://localhost:8082/apis/registry/v3}
%dev.kafka.bootstrap.servers=localhost:9094

# Test mode: Apicurio Registry URL with default
%test.apicurio.registry.url=${APICURIO_REGISTRY_URL:http://localhost:8082/apis/registry/v3}
%test.kafka.bootstrap.servers=localhost:9095
```

## Environment-Specific Setup

### Development

Uses real Kafka/Apicurio with database persistence:

```properties
%dev.kafka.bootstrap.servers=localhost:9094
%dev.apicurio.registry.url=http://localhost:8082/apis/registry/v3
```

### Testing

Uses Compose DevServices for automatic test infrastructure:

```properties
%test.quarkus.compose.devservices.enabled=true
%test.quarkus.compose.devservices.files=src/test/resources/compose-test-services.yml
%test.kafka.bootstrap.servers=localhost:9095
%test.apicurio.registry.url=http://localhost:8082/apis/registry/v3
```

### Production

Configure via environment variables:

```properties
kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS}
apicurio.registry.url=${APICURIO_REGISTRY_URL}
```

## What Gets Configured Automatically

The extension automatically configures:

- ✅ **Connector**: `smallrye-kafka` for all channels
- ✅ **Key Serializer/Deserializer**: `UUIDSerializer` / `UUIDDeserializer`
- ✅ **Value Serializer/Deserializer**: Apicurio Protobuf serializers
- ✅ **Apicurio Registry URL**: Bridged from `apicurio.registry.url`
- ✅ **Topic Names**: Derived from channel names
- ✅ **Return Class**: For consumers (automatically detected from method signature)
- ✅ **Reliability Settings**: Platform-standard configurations

## Classloading

The extension automatically configures parent-first classloading for:

- `com.google.protobuf:protobuf-java`
- `io.grpc:*` (all gRPC artifacts)
- `ai.pipestream:grpc-stubs`
- `io.smallrye.reactive:mutiny`
- `org.jctools:jctools-core`

**No manual classloading configuration needed!**

## Troubleshooting

### Extension Not Loading

**Check build logs** for extension discovery:
```
🔌 [Extension] Pipeline Protobuf Kafka Connector extension is loading...
⚡ [Extension] Starting configuration generation...
⚡ [Extension] Configured PRODUCER my-channel -> ai.pipestream.MyEvent
```

If you don't see these logs, verify:
- Extension dependency is in `build.gradle`
- Version matches published artifact
- Extension metadata is in JAR (`META-INF/quarkus-extension.properties`)

### ClassNotFoundException / NoClassDefFoundError

If you see classloading errors (especially for gRPC classes):
- Ensure you're using the latest extension version (includes all parent-first artifacts)
- Check that `quarkus-extension.properties` includes parent-first artifacts
- Verify no manual classloading configuration conflicts

### Messages Not Being Sent/Received

- **Infrastructure URLs set?** Check `kafka.bootstrap.servers` and `apicurio.registry.url`
- **Annotations present?** Both `@Channel`/`@Incoming` AND `@ProtobufChannel`/`@ProtobufIncoming` required
- **Channel names correct?** Use different names for producers and consumers
- **Topic exists?** Check Kafka to verify topics are created

### Serialization Errors

- **Protobuf classes?** Extension only works with Protobuf messages from `grpc-stubs`
- **Consumer signature?** Must use `ConsumerRecord<UUID, YourProtobufType>`
- **Schema registered?** Check Apicurio Registry UI for registered schemas

## Architecture

### Module Structure

- **runtime** (`pipeline-protobuf-kafka-connector`): Contains annotations and runtime dependencies
- **deployment** (`pipeline-protobuf-kafka-connector-deployment`): Build-time processor that generates configuration
- **integration-tests**: Example usage and tests

### Build-Time Processing

The `ProtobufKafkaProcessor` runs during Quarkus augmentation:

1. Scans Jandex index for `@ProtobufChannel` and `@ProtobufIncoming` annotations
2. Extracts Protobuf message types from method parameters and field types
3. Generates `SystemPropertyBuildItem`s for SmallRye configuration
4. Registers `ApicurioConfigurator` as a CDI bean

### Runtime Configuration

The `ApicurioConfigurator` intercepts SmallRye's `KafkaClientCustomizer` to:
- Bridge `apicurio.registry.url` from application properties
- Ensure Apicurio serializers/deserializers have access to registry URL

## Status

✅ **Production Ready** - Used in platform-registration-service and other core services.

## See Also

- [Kafka Configuration Guide](../pipeline-commons/KAFKA_CONFIGURATION.md) - Comprehensive user guide
- [Pipeline Commons](../pipeline-commons/README.md) - Shared utilities and configuration
