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
implementation platform('ai.pipestream:pipeline-bom:0.2.8-SNAPSHOT') // or later
implementation 'ai.pipestream:pipeline-commons'
```

### 2. Configuring a Channel (Producer)
In your `application.properties`, you only need to define the connector and topic:

```properties
# 1. Define the connector (Triggers the global defaults)
mp.messaging.outgoing.my-channel.connector=smallrye-kafka

# 2. Define the topic
mp.messaging.outgoing.my-channel.topic=my-topic-name

# 3. (Optional) Specific Protobuf Message Name
# Useful if your .proto file defines multiple messages
mp.messaging.outgoing.my-channel.apicurio.registry.proto.message-name=MyEvent
```

### 3. Configuring a Channel (Consumer)
Consumers need one extra line to tell the deserializer which Java class to instantiate:

```properties
# 1. Connector
mp.messaging.incoming.my-channel.connector=smallrye-kafka

# 2. Topic
mp.messaging.incoming.my-channel.topic=my-topic-name

# 3. Return Class (Required for Protobuf deserialization)
mp.messaging.incoming.my-channel.apicurio.registry.deserializer.value.return-class=ai.pipestream.MyEventClass
```

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
