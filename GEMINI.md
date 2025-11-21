# Gemini AI Assistant Context for the Pipestream Platform

This document contains the core principles, architectural standards, and non-negotiable rules for the Pipestream AI platform. Adherence to these rules is mandatory to prevent common failure modes and maintain platform integrity.

## 1. Core Principles

- **The Standard is Not Optional:** The patterns described here are the result of significant engineering effort to solve specific, recurring problems. Deviations are not permitted.
- **Enforce Strictness:** The platform prefers strict, compile-time contracts (Protobuf, specific Java types) over flexible or dynamic ones (`DynamicMessage`, `String`). This is a deliberate choice to prevent silent failures and data corruption.
- **Test Against Reality:** All tests involving external services (like Kafka, Apicurio, OpenSearch) **MUST** run against real instances of those services, managed via Docker Compose. In-memory versions are forbidden for testing these integrations.

## 2. Source of Truth for Code Patterns

- **The `reference-code` Repository is Supreme:** Before consulting any external resource (Google, Stack Overflow, etc.), you **MUST** first look for a working example in the `ai-pipestream/reference-code` repository.
- **Purpose:** This repository is a script-driven collection of golden-path implementations for all core platform tasks (e.g., Kafka producers/consumers, gRPC services, database access). It is the absolute source of truth for how to build services correctly.
- **Mandate:** If a pattern exists in `reference-code`, it must be followed. If a pattern does not exist, the `reference-code` repository should be updated with a new example once the pattern is established.

## 3. The Kafka Standard (The "Tattoo")

This is the most critical and non-negotiable part of the platform. The "hell" of inconsistent messaging standards must be avoided at all costs.

### **Configuration**
- **Centralized Config is Law:** All Kafka channel configuration is handled by `PipelineKafkaConfigSource` in the `pipeline-commons` library.
- **`application.properties` MUST be Minimal:** A developer only needs to define the `connector` and `topic` for each channel. All other properties (serializers, deserializers, Apicurio URL, etc.) are applied automatically. Any attempt to manually define serializers is a violation of the standard.

**Correct Producer Config:**
```properties
mp.messaging.outgoing.my-channel.connector=smallrye-kafka
mp.messaging.outgoing.my-channel.topic=my-topic
```

### **Data Contracts**
- **Key:** The key for **ALL** Kafka messages **MUST** be `java.util.UUID`. No other type is acceptable.
- **Value:** The value **MUST** be a specific, compiled **Protobuf `Message`**.
- **FORBIDDEN TYPES:**
    - `org.apache.kafka.common.serialization.StringSerializer` or `StringDeserializer` for keys.
    - `io.quarkus.kafka.client.serialization.JsonbSerializer` or any other JSON serializer.
    - `io.apicurio.registry.serde.protobuf.DynamicMessage` as a consumer type.
    - `java.lang.String` as a message value.

### **Apicurio Schema Registry**
- **Apicurio is Mandatory:** All Protobuf schemas for Kafka messages **MUST** be managed by the Apicurio Schema Registry. Bypassing it is not an option.
- **Configuration is Automatic:** The URL and registry settings are handled by `PipelineKafkaConfigSource`.

### **Application Code**
- **Producer:** Producers should inject an `Emitter<YourProtobufClass>`. Key generation is handled automatically by the framework.
- **Consumer:** The required method signature for an `@Incoming` consumer is `public Uni<Void> consume(ConsumerRecord<UUID, YourProtobufClass> record)`. This provides access to the UUID key and the strongly-typed Protobuf value.

## 4. Testing Standards

- **Use `compose-devservices`:** The standard for managing test environments (Kafka, Apicurio, MySQL, etc.) is a `docker-compose.yml` file in `src/test/resources/`. The `quarkus-compose-devservices` extension manages its lifecycle.
- **Producer Tests:** To test a producer, the test code creates a manual `KafkaConsumer` to subscribe to the topic and verify the output. This manual consumer MUST be configured to expect a `UUID` key and the correct Protobuf value.
- **Consumer Tests:** To test a consumer, the test code creates a manual `KafkaProducer` to send a message to the topic. This producer MUST be configured to send a `UUID` key. Downstream services called by the consumer should be mocked using `@InjectMock`.

## 5. Documentation Workflow

- **Public Docs:** The main documentation site lives in the `ai-pipestream/.github` repository and is built with `docsify.js`.
- **Developer Guides:** Core technical guides are located in the `docs/developer/` directory.
- **RFCs:** Proposals for new features or changes ("Hamster Wheel") are located in `docs/hamster-wheel/` as RFC documents.
- **`dev-assets`:** This repository is a staging/holding area for legacy documents or assets not yet ready for the main documentation.

## 6. Future Enhancements (Tracked Ideas)

- **Custom `MessageConverter`:** There is a proposal to simplify consumer signatures further by creating a custom `MessageConverter` to automatically unwrap the `ConsumerRecord`. (See `platform-libraries#39` and `RFC-001`). Until this is implemented, the `ConsumerRecord<UUID, T>` signature is the ruling standard.

## 7. Project Repositories

This table lists the repositories within the `ai-pipestream` GitHub organization.

| Repository | Description |
|---|---|
| **Core Services** | |
| `account-service` | User/account management. |
| `connector-admin` | Service to administer external connectors. |
| `connector-intake-service` | Gateway for document intake (stateless refactor). |
| `mapping-service` | Document mapping/transformation. |
| `opensearch-manager` | OpenSearch indexing & management. |
| `platform-registration-service` | Service discovery & registration (Consul integration). |
| `repository-service` | Document storage (Redis + S3 refactor). |
| **Modules (Pipeline Components)** | |
| `module-chunker` | Document chunking. |
| `module-echo` | Simple test/validation module. |
| `module-embedder` | Vector embeddings. |
| `module-opensearch-sink` | OpenSearch sink module. |
| `module-parser` | Document parsing. |
| `module-pipeline-probe` | Testing harness (needs update after frontend standardization). |
| `module-proxy` | Proxy for gRPC services in other languages, enabling use of platform controls. |
| **Platform & Libraries** | |
| `.github` | Contains the main public-facing documentation website. |
| `platform-frontend` | Node.js service for the platform frontend (Vue). |
| `platform-libraries` | Common Java libraries for the platform. **(This is the current project)** |
| `tika4-shaded` | A custom-built fork of Tika 4.0. |
| **Development & Samples** | |
| `dev-assets` | Scripts, dev environment setup, sample containers, and tutorials. |
| `sample-documents` | A set of sample documents for testing parsers and harnesses. |
| **Archived/Legacy** | |
| `pipestream-engine` | Old orchestration engine. Code is for reference only. |

*Note: The `reference-code` repository is not listed as a deployable project but is the primary source of truth for all coding patterns.*