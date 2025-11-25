# Kafka Configuration Guide

## Overview
The Pipeline Platform uses a centralized configuration strategy for Kafka to ensure consistency, reliability, and ease of use. This configuration is provided by the `ai.pipestream.common.config.PipelineKafkaConfigSource` class in `pipeline-commons`.

## How It Works
Instead of repeating 10-15 lines of configuration for every Kafka channel in every service, the platform injects global defaults at the **Connector** level (`mp.messaging.connector.smallrye-kafka`).

This means any channel using `connector=smallrye-kafka` automatically inherits:
*   **Serialization:** UUID keys, Protobuf values.
*   **Registry:** Apicurio Registry integration with `TopicIdStrategy`.
*   **Reliability:** `acks=all`, `idempotence=true` (Producers), `auto.offset.reset=earliest` (Consumers).
*   **Performance:** optimized batching and compression (`snappy`, `linger.ms=20`).

## Usage

### 1. Dependencies
Ensure your service depends on `pipeline-commons`:
```groovy
implementation platform('ai.pipestream:pipeline-bom:0.2.10') // or later
implementation 'ai.pipestream:pipeline-commons'
```

### 2. Configuring a Channel (Producer)
In your `application.properties`, you only need to define the connector and topic. The extension handles the rest!

```properties
# 1. Define the connector (Triggers the global defaults)
mp.messaging.outgoing.my-channel.connector=smallrye-kafka

# 2. Define the topic
mp.messaging.outgoing.my-channel.topic=my-topic-name
```

**That's it!** The extension automatically applies:
*   `UUIDSerializer` for keys
*   `ProtobufKafkaSerializer` for values
*   `auto-register=true` for schemas
*   `proto.message-name` (inferred from your `Emitter<T>` type!)

### 3. Configuring a Channel (Consumer)
Consumers are just as easy. The extension infers the return class from your `@Incoming` method signature.

```properties
# 1. Connector
mp.messaging.incoming.my-channel.connector=smallrye-kafka

# 2. Topic
mp.messaging.incoming.my-channel.topic=my-topic-name
```

**That's it!** The extension automatically applies:
*   `UUIDDeserializer` for keys
*   `ProtobufKafkaDeserializer` for values
*   `return-class` (inferred from `consume(ConsumerRecord<UUID, MyProtoType> record)`)

### 4. Zero-Config via Environment Variables
You can even skip `application.properties` entirely! Use environment variables to configure topics dynamically.

*   **Format:** `PIPELINE_TOPIC_{TOPIC_NAME}={topic-name}`
*   **Direction:** The extension guesses if it's incoming or outgoing based on the channel name (e.g., contains "in", "consumer", "out", "producer").

**Example:**
`PIPELINE_TOPIC_USER_EVENTS=user-events-v1`

If you have a channel named `user-events` in your code, the extension will automatically configure it to use the `user-events-v1` topic.

## Overriding Defaults
If a specific channel needs different settings (e.g., JSON serialization instead of Protobuf), you can override the defaults in `application.properties`:

```properties
mp.messaging.outgoing.legacy-channel.connector=smallrye-kafka
mp.messaging.outgoing.legacy-channel.value.serializer=org.apache.kafka.common.serialization.StringSerializer
```

## Debugging
If configuration seems missing:
1.  Check that `pipeline-commons` is in your classpath.
2.  Verify that `PipelineKafkaConfigSource` is registered in `META-INF/services/org.eclipse.microprofile.config.spi.ConfigSource`.
3.  Run `./gradlew dependencies` to ensure you aren't pulling an old version of `pipeline-commons`.

## Testing Configuration

For integration tests, we **DO NOT** rely on the default Quarkus Kafka DevServices (which spins up a random container). Instead, we use a deterministic approach with Docker Compose and programmatic configuration.

### 1. Infrastructure (`compose-test-services.yml`)
Define your test infrastructure (Redpanda, Apicurio, etc.) in a `src/test/resources/compose-test-services.yml` file.

### 2. Configuration (`application.properties`)
Use the `%test` profile to configure the test environment. The extension works seamlessly with `quarkus-compose-devservices`.

```properties
# Enable Compose Dev Services
%test.quarkus.compose.devservices.enabled=true
%test.quarkus.compose.devservices.files=src/test/resources/compose-test-services.yml

# Configure Apicurio URL (exposed by Compose)
%test.mp.messaging.connector.smallrye-kafka.apicurio.registry.url=${APICURIO_REGISTRY_URL:http://localhost:8082/apis/registry/v3}

# Kafka Bootstrap Servers (exposed by Compose)
%test.kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9095}
```

No manual `KafkaTestResource` is required for standard setups!

### 3. Usage in Test
Annotate your test class:

```java
@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
public class MyConsumerTest { ... }
```
