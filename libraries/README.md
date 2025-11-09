# Pipeline Libraries

Shared libraries for the Pipeline Engine project. These libraries provide common functionality, utilities, and APIs used across all Pipeline services and modules.

## Building the Libraries

This section provides instructions for building the libraries from source, whether you have access to the private internal repositories or are building from the public internet.

### Prerequisites

- **Java 21+** (OpenJDK or Oracle JDK)
- **Gradle** (uses Gradle wrapper, no installation required)
- **Git** (for cloning the repository)

### Building with Public Repositories (GitHub CI)

If you've downloaded this project from GitHub and only have access to the public internet (no access to private maven.rokkon.com repositories), use the `-PusePublicRepos=true` flag:

```bash
# Clone the repository (if not already done)
git clone https://github.com/io-pipeline/libraries.git
cd libraries

# Build all libraries using public repositories
./gradlew build --no-daemon --build-cache -PusePublicRepos=true
```

#### GitHub Packages Authentication

The libraries depend on the **Pipeline BOM** which is published to GitHub Packages. GitHub Packages **requires authentication** even for public packages.

**Create a GitHub Personal Access Token:**

1. Go to https://github.com/settings/tokens
2. Click "Generate new token (classic)"
3. Select scope: `read:packages`
4. Generate and copy the token

**Build with Authentication:**

```bash
# Set environment variables
export GITHUB_ACTOR=your-github-username
export GITHUB_TOKEN=your-github-personal-access-token

# Build the libraries
./gradlew build --no-daemon --build-cache -PusePublicRepos=true
```

**Publish to GitHub Packages:**

```bash
# Only on main branch after successful build
./gradlew publishAllPublicationsToGitHubPackagesRepository -PusePublicRepos=true
```

### Building with Private Repositories (Default)

If you have access to the internal maven.rokkon.com repository (development on private network):

```bash
# Build all libraries (uses fast internal mirrors)
./gradlew build --no-daemon --build-cache

# Publish to local Maven repository
./gradlew publishAllToMavenLocal

# Publish to both Gitea and Reposilite (requires credentials)
./gradlew publishAll
```

This mode provides:
- ✅ Faster builds using internal repository mirrors
- ✅ Access to snapshot dependencies not yet published publicly
- ✅ Publishing to internal Gitea and Reposilite repositories

### How It Works

The build system uses conditional repository configuration in `settings.gradle`:

- **Without `-PusePublicRepos`** (default): Uses fast internal maven.rokkon.com repositories
- **With `-PusePublicRepos=true`**: Uses public Maven Central and GitHub Packages

When `-PusePublicRepos=true` is set:
- Gradle wrapper downloads from `services.gradle.org` (public)
- Dependencies resolve from Maven Central (public)
- BOM resolves from GitHub Packages (public, requires auth)
- Apache Tika snapshots from Apache's public repository

### Build Outputs

After building, you'll find:

```
libraries/
├── pipeline-api/build/libs/
│   ├── pipeline-api-1.0.0-SNAPSHOT.jar
│   ├── pipeline-api-1.0.0-SNAPSHOT-sources.jar
│   └── pipeline-api-1.0.0-SNAPSHOT-javadoc.jar
├── pipeline-commons/build/libs/
│   ├── pipeline-commons-1.0.0-SNAPSHOT.jar
│   ├── pipeline-commons-1.0.0-SNAPSHOT-sources.jar
│   └── pipeline-commons-1.0.0-SNAPSHOT-javadoc.jar
└── [other libraries...]
```

### Common Build Tasks

```bash
# Clean all build artifacts
./gradlew clean

# Build without tests (faster)
./gradlew build -x test -PusePublicRepos=true

# Build a specific library
./gradlew :pipeline-api:build -PusePublicRepos=true

# Run tests only
./gradlew test -PusePublicRepos=true

# Check for dependency updates
./gradlew dependencyUpdates -PusePublicRepos=true
```

### Troubleshooting Public Builds

**Issue: 401 Unauthorized accessing GitHub Packages**

```bash
# Solution: GitHub Packages requires authentication even for public packages
# Create a personal access token and set environment variables:
export GITHUB_ACTOR=your-github-username
export GITHUB_TOKEN=your-github-personal-access-token
./gradlew build -PusePublicRepos=true --refresh-dependencies
```

**Issue: Connection timeout to maven.rokkon.com**

```bash
# Solution: Ensure -PusePublicRepos=true is set to skip private repositories
./gradlew build -PusePublicRepos=true
```

**Issue: Build works but tests fail**

```bash
# Solution: Some tests may require infrastructure services (Consul, etc.)
# Skip tests for initial build:
./gradlew build -x test -PusePublicRepos=true
```

**Issue: Gradle daemon issues**

```bash
# Solution: Stop all Gradle daemons and retry
./gradlew --stop
./gradlew build -PusePublicRepos=true --no-daemon
```

## 📚 Libraries

| Library | Description | Key Features |
|---------|-------------|--------------|
| **pipeline-api** | Core API types and interfaces | Events, annotations, gRPC interfaces |
| **pipeline-commons** | Common utilities and helpers | Protobuf utilities, file system helpers |
| **dynamic-grpc** | Dynamic gRPC client management | Service discovery, channel management, load balancing |
| **dynamic-grpc-registration-clients** | Platform registration clients | Service registration helpers |
| **data-util** | Test data and utilities | Shared test fixtures, data generators |
| **testing-utils** | Testing framework components | Consul integration tests, test utilities |
| **validators** | Validation utilities | JSON schema validation, config validators |

## 🏗️ Build Structure

This directory is a **Gradle composite build** - a separate Gradle project that can be built independently or included in the main build.

### Key Configuration Files

- `settings.gradle` - Composite build configuration, imports parent version catalog
- `build.gradle` - Common configuration for all libraries
- Individual `*/build.gradle` - Library-specific dependencies

### Version Management

Libraries use the **parent project's version catalog** (`../gradle/libs.versions.toml`) for dependency versions:

```groovy
// libraries/settings.gradle
dependencyResolutionManagement {
    versionCatalogs {
        libs {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
```

This ensures:
- ✅ Single source of truth for versions
- ✅ Consistency across main build and libraries
- ✅ No duplicate version definitions

All dependency versions are managed by the **BOM** (`ai.pipestream:pipeline-bom:1.0.0-SNAPSHOT`).

## 🚀 Publishing

### Local Development

Publish to Maven Local for use in local development:

```bash
cd libraries
../gradlew publishAllToMavenLocal
```

This publishes all libraries to `~/.m2/repository/io/pipeline/`

### Production Release

Publish to both Maven Local and GitHub Packages:

```bash
cd libraries
../gradlew publishAll
```

### Individual Library

Publish a single library:

```bash
cd libraries
../gradlew :pipeline-api:publishToMavenLocal
../gradlew :pipeline-api:publish  # To GitHub Packages
```

### GitHub Packages Authentication

**Local Development:**
- Uses `GH_USER` and `GH_PAT` environment variables (from your `~/.bashrc`)

**CI/CD:**
- Falls back to `GITHUB_ACTOR` and `GITHUB_TOKEN` environment variables

## 📦 Published Artifacts

Each library publishes:
- **Main JAR** - Compiled classes
- **Sources JAR** - Source code for IDE navigation
- **Javadoc JAR** - API documentation
- **POM** - Maven metadata with dependencies

## 🔧 Development Workflow

### 1. Make Changes to a Library

Edit code in any library (e.g., `pipeline-api/src/main/java/...`)

### 2. Publish to Maven Local

```bash
cd libraries
../gradlew :pipeline-api:publishToMavenLocal
```

### 3. Use in Applications

Applications automatically pick up the published version from Maven Local:

```groovy
dependencies {
    implementation 'ai.pipestream:pipeline-api:1.0.0-SNAPSHOT'
}
```

### 4. Test in Main Build

From the project root:

```bash
./gradlew :applications:repo-service:build
```

The main build includes libraries via composite build, so it can also use unpublished changes during development.

## 🎯 Benefits of Composite Build

✅ **Independent Building** - Libraries can be built separately  
✅ **No Service Restarts** - Changing libraries doesn't restart running services  
✅ **IDE Support** - IntelliJ/Eclipse loads as separate Gradle module  
✅ **Version Control** - Libraries can have independent release cycles  
✅ **Distribution** - Publish to GitHub Packages for team/external use  

## 📋 Common Tasks

### Build All Libraries

```bash
../gradlew build
```

### Clean All Libraries

```bash
../gradlew clean
```

### Run Tests

```bash
../gradlew test
```

### Check for Dependency Updates

```bash
../gradlew dependencyUpdates
```

## 🔍 Dependency Graph

```
pipeline-api (base)
    └─> grpc-stubs

pipeline-commons
    └─> pipeline-api
    └─> grpc-stubs

dynamic-grpc
    └─> pipeline-api
    └─> pipeline-commons
    └─> grpc-stubs

dynamic-grpc-registration-clients
    └─> dynamic-grpc
    └─> pipeline-api
    └─> grpc-stubs

data-util
    └─> pipeline-api
    └─> pipeline-commons
    └─> grpc-stubs

testing-utils
    (standalone - test utilities only)

validators
    └─> pipeline-api
    └─> pipeline-commons
    └─> data-util
    └─> testing-utils
```

## 📝 Adding a New Library

1. Create new directory: `libraries/my-new-library/`

2. Add to `settings.gradle`:
   ```groovy
   include 'my-new-library'
   ```

3. Create `my-new-library/build.gradle`:
   ```groovy
   plugins {
       alias(libs.plugins.java.library)
       alias(libs.plugins.quarkus)
       alias(libs.plugins.jandex)
   }
   
   dependencies {
       // Dependencies (versions from BOM)
       implementation 'ai.pipestream:grpc-stubs:1.0.0-SNAPSHOT'
       implementation project(':pipeline-api')
       
       testImplementation libs.quarkus.junit5
   }
   
   apply plugin: 'maven-publish'
   
   publishing {
       publications {
           maven(MavenPublication) {
               from components.java
               pom {
                   name = 'My New Library'
                   description = 'Description of my library'
               }
           }
       }
   }
   ```

4. Add to BOM (`../bom/build.gradle`):
   ```groovy
   constraints {
       api("ai.pipestream:my-new-library:1.0.0-SNAPSHOT")
   }
   ```

5. Publish:
   ```bash
   ../gradlew :my-new-library:publishToMavenLocal
   ```

## 🐛 Troubleshooting

### Libraries not found in main build

**Solution:** Publish to Maven Local first:
```bash
cd libraries && ../gradlew publishAllToMavenLocal
```

### Version conflicts

**Solution:** All versions are managed by the BOM. Check `../bom/build.gradle` for version constraints.

### GitHub Packages authentication fails

**Solution:** Ensure `GH_USER` and `GH_PAT` environment variables are set:
```bash
echo $GH_USER
echo $GH_PAT
```

### Javadoc warnings

Javadoc warnings are normal and don't affect the build. To suppress:
```groovy
tasks.withType(Javadoc) {
    options.addStringOption('Xdoclint:none', '-quiet')
}
```

## 📚 Additional Resources

- [Gradle Composite Builds](https://docs.gradle.org/current/userguide/composite_builds.html)
- [GitHub Packages Maven](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)
- [Quarkus Extensions](https://quarkus.io/guides/writing-extensions)

## 🔗 Related Documentation

- Main project: `../README.md`
- Node libraries: `../node/README.md`
- gRPC stubs: `../grpc/README.md`
- BOM: `../bom/README.md`
