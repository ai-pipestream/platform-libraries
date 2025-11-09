# Pipeline BOM (Bill of Materials)

## Overview

This Bill of Materials (BOM) manages dependency versions across all Pipeline Engine microservices. It provides a centralized version catalog and ensures consistency across the entire platform.

## Version Strategy

### Quarkus First
We align with the latest stable Quarkus release and favor Quarkus-managed dependency versions wherever possible. This ensures compatibility with Quarkus's dependency model and tested integration.

### gRPC & Protocol Buffers
We use the **highest supported stable versions** of:
- **gRPC** - Latest stable release for optimal performance and features
- **Protocol Buffers (protobuf)** - Matched to gRPC compatibility requirements
- **Connect-ES** - Latest for TypeScript/JavaScript clients

These versions override Quarkus BOM versions using `strictly()` to ensure consistent gRPC stack across all services.

### Apicurio Registry
We track the **latest stable Apicurio versions** for:
- Schema Registry integration
- Protobuf schema serialization/deserialization
- Registry client SDKs

### Apache Tika (Pre-release)
Currently using **Apache Tika 4.x SNAPSHOT** builds from Apache's snapshot repository until the official 4.0 release. Tika 4.x provides major improvements for document parsing and metadata extraction.

**Repository:** `https://repository.apache.org/snapshots/`

Once Tika 4.0 is released, we'll migrate to the stable version from Maven Central.

### Other Key Dependencies
- **Kafka** - Aligned with Quarkus recommendations
- **OpenSearch** - Latest stable Java client
- **WireMock** - Latest for integration testing with gRPC support
- **Security** - password4j for password hashing
- **Testing** - SmallRye Reactive Messaging In-Memory for testing

## Publishing

This BOM is published to three locations:

1. **Local Maven** (`~/.m2/repository`) - For local development
2. **Gitea Maven Registry** (`https://git.rokkon.com/api/packages/io-pipeline/maven`) - Internal artifact repository
3. **GitHub Packages** (releases only) - Public releases

### Gradle Version Catalog

The BOM also publishes a **Gradle version catalog** (`pipeline-bom-catalog`) that can be imported into projects:

```gradle
dependencyResolutionManagement {
    versionCatalogs {
        libs {
            from("ai.pipestream:pipeline-bom-catalog:1.0.0-SNAPSHOT")
        }
    }
}
```

## Usage

### In Gradle (with BOM)

```gradle
dependencies {
    implementation platform('ai.pipestream:pipeline-bom:1.0.0-SNAPSHOT')
    implementation 'io.quarkus:quarkus-grpc'
    implementation 'io.grpc:grpc-protobuf'
    // Version is managed by BOM
}
```

### In Gradle (with Version Catalog)

```gradle
dependencies {
    implementation libs.quarkus.grpc
    implementation libs.grpc.protobuf
    // Versions from catalog
}
```

## Maintenance

When updating versions:
1. Check Quarkus release notes for dependency updates
2. Verify gRPC/protobuf compatibility matrix
3. Test with integration test suite before publishing
4. Update this README if version strategy changes

## CI/CD

- **Gitea Actions** - Builds and publishes on every commit to `main`
- **GitHub Actions** - Nightly builds and publishes releases
- Uses organization secrets: `GIT_USER`, `GIT_PAT` for authentication
