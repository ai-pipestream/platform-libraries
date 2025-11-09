# Pipeline gRPC Stubs

Generated gRPC stubs for Pipeline Engine services. Published as both Java artifacts and TypeScript/JavaScript packages.

## Modules

### grpc-stubs (Java)
Protocol Buffer and gRPC stubs for all Pipeline services built with Quarkus gRPC.

**Artifact:** `ai.pipestream:grpc-stubs:1.0.0-SNAPSHOT`

### grpc-google-descriptor (Java)
Google protobuf descriptor utilities and helpers.

**Artifact:** `ai.pipestream:grpc-google-descriptor:1.0.0-SNAPSHOT`

### node (TypeScript/JavaScript)
Connect-ES compatible TypeScript stubs for browser and Node.js clients.

**Package:** `@io-pipeline/grpc-stubs`

## Publishing

Published to:
- **Gitea Maven Registry:** `https://git.rokkon.com/api/packages/io-pipeline/maven`
- **Gitea npm Registry:** `https://git.rokkon.com/api/packages/io-pipeline/npm`
- **GitHub Packages:** `https://maven.pkg.github.com/io-pipeline/grpc` (releases)

## CI/CD

- **Gitea Actions:** Builds Java + Node packages on every commit
- **GitHub Actions:** Nightly builds and releases
