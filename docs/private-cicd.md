# Private CI/CD Deployment Guide

This guide explains how to deploy this project to a private, air-gapped CI/CD environment (e.g., internal GitLab) without internet connectivity.

## Prerequisites

- Private Maven repository (Nexus, Artifactory, or GitLab Package Registry)
- Private CI/CD system (GitLab CI, Jenkins, etc.)
- Network proxy (if any external dependencies needed)

## Initial Setup: Create SCM Version Tag

**IMPORTANT:** This project uses Axion Release plugin for versioning, which determines the version from git tags. For a **fresh repository clone/install**, you MUST create an initial version tag first:

```bash
# On your fresh repository
git tag v0.1.0
git push origin v0.1.0
```

Without this initial tag, the build will fail with version-related errors because Axion cannot determine the project version.

After creating the initial tag:
- Current version will be `v0.1.0` (on the tag)
- Main branch will be `v0.1.1-SNAPSHOT` (next patch version)
- All builds will have proper version numbers

## Key Concepts

### Why BOM Needs Local Publishing First

The BOM (Bill of Materials) module must be published to Maven Local **before** building other modules because:
1. Subprojects depend on the BOM for dependency management
2. During the build, Gradle resolves the BOM from repositories
3. Without local publishing, circular dependency errors occur

This is why the workflow has:
```bash
./gradlew :bom:publishToMavenLocal --no-daemon
./gradlew build --no-daemon
```

## Configuration Changes

### 1. Configure Maven Repository (Global Settings)

**Option A: Using `gradle.properties` (Recommended)**

Create/edit `~/.gradle/gradle.properties`:

```properties
# Private Maven Repository
mavenPrivateUrl=https://nexus.internal.company.com/repository/maven-releases/
mavenPrivateSnapshotUrl=https://nexus.internal.company.com/repository/maven-snapshots/
mavenPrivateUsername=your-username
mavenPrivatePassword=your-password

# GPG Signing (if required)
signing.keyId=YOUR_KEY_ID
signing.password=YOUR_KEY_PASSWORD
signing.secretKeyRingFile=/path/to/secring.gpg

# Proxy settings (if needed)
systemProp.http.proxyHost=proxy.internal.company.com
systemProp.http.proxyPort=8080
systemProp.https.proxyHost=proxy.internal.company.com
systemProp.https.proxyPort=8080
```

**Option B: Using Environment Variables**

```bash
export MAVEN_PRIVATE_URL=https://nexus.internal.company.com/repository/maven-releases/
export MAVEN_PRIVATE_SNAPSHOT_URL=https://nexus.internal.company.com/repository/maven-snapshots/
export MAVEN_PRIVATE_USERNAME=your-username
export MAVEN_PRIVATE_PASSWORD=your-password
```

### 2. Update Project Configuration

Edit `build.gradle` to add your private repository:

```gradle
allprojects {
    group = 'ai.pipestream'
    version = scmVersion.version

    repositories {
        mavenLocal()
        // Add your private repository
        maven {
            url = findProperty('mavenPrivateUrl') ?: System.getenv('MAVEN_PRIVATE_URL')
            credentials {
                username = findProperty('mavenPrivateUsername') ?: System.getenv('MAVEN_PRIVATE_USERNAME')
                password = findProperty('mavenPrivatePassword') ?: System.getenv('MAVEN_PRIVATE_PASSWORD')
            }
        }
        mavenCentral() // Fallback or remove for air-gapped
    }
}

// Configure publishing
subprojects {
    pluginManager.withPlugin('maven-publish') {
        publishing {
            repositories {
                maven {
                    name = "PrivateMaven"
                    url = project.version.endsWith('SNAPSHOT')
                        ? (findProperty('mavenPrivateSnapshotUrl') ?: System.getenv('MAVEN_PRIVATE_SNAPSHOT_URL'))
                        : (findProperty('mavenPrivateUrl') ?: System.getenv('MAVEN_PRIVATE_URL'))
                    credentials {
                        username = findProperty('mavenPrivateUsername') ?: System.getenv('MAVEN_PRIVATE_USERNAME')
                        password = findProperty('mavenPrivatePassword') ?: System.getenv('MAVEN_PRIVATE_PASSWORD')
                    }
                }
            }
        }
    }
}

// Aggregate task for private publishing
tasks.register('publishAllToPrivate') {
    group = 'publishing'
    description = 'Publish all subprojects to private Maven repository'
    dependsOn subprojects.findAll { it.tasks.findByName('publishMavenPublicationToPrivateMavenRepository') }
        .collect { it.tasks.named('publishMavenPublicationToPrivateMavenRepository') }
}
```

### 3. Replace Maven Central Publishing

If you want to **completely replace** Maven Central with your private repository, modify `build.gradle`:

```gradle
// Remove or comment out Maven Central plugin
// id 'com.gradleup.nmcp.aggregation' version '1.2.0'

// Use standard publishing instead
tasks.register('publishAggregation') {
    group = 'publishing'
    description = 'Publish all modules to private repository'

    // Publish BOM first
    dependsOn ':bom:publishToMavenLocal'
    dependsOn ':bom:publishMavenPublicationToPrivateMavenRepository'

    // Then publish everything else
    dependsOn subprojects
        .findAll { it.name != 'bom' }
        .findAll { it.tasks.findByName('publishMavenPublicationToPrivateMavenRepository') }
        .collect { it.tasks.named('publishMavenPublicationToPrivateMavenRepository') }
}
```

## GitLab CI/CD Configuration

Create `.gitlab-ci.yml`:

```yaml
variables:
  GRADLE_OPTS: "-Dorg.gradle.daemon=false"
  MAVEN_PRIVATE_URL: "https://gitlab.internal.company.com/api/v4/projects/${CI_PROJECT_ID}/packages/maven"
  MAVEN_PRIVATE_USERNAME: "gitlab-ci-token"
  MAVEN_PRIVATE_PASSWORD: "${CI_JOB_TOKEN}"

stages:
  - build
  - publish-snapshot
  - release
  - publish-release

before_script:
  - export GRADLE_USER_HOME=$(pwd)/.gradle

# Build on every commit
build:
  stage: build
  script:
    - ./gradlew :bom:publishToMavenLocal --no-daemon
    - ./gradlew build --no-daemon
  artifacts:
    paths:
      - build/libs/
      - "*/build/libs/"
  cache:
    paths:
      - .gradle/wrapper
      - .gradle/caches

# Publish snapshot on main branch
publish-snapshot:
  stage: publish-snapshot
  only:
    - main
  script:
    - ./gradlew :bom:publishToMavenLocal --no-daemon
    - ./gradlew build --no-daemon
    - ./gradlew publishAllToPrivate --no-daemon
  needs:
    - build

# Manual release trigger
release:
  stage: release
  when: manual
  only:
    - main
  script:
    - |
      # Get latest tag
      LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "v0.0.0")
      VERSION=${LATEST_TAG#v}
      IFS='.' read -r -a VERSION_PARTS <<< "$VERSION"
      MAJOR="${VERSION_PARTS[0]}"
      MINOR="${VERSION_PARTS[1]}"
      PATCH="${VERSION_PARTS[2]}"

      # Increment patch version (or customize)
      PATCH=$((PATCH + 1))
      NEW_VERSION="${MAJOR}.${MINOR}.${PATCH}"
      NEW_TAG="v${NEW_VERSION}"

      # Create and push tag
      git config user.name "GitLab CI"
      git config user.email "ci@company.internal"
      git tag -a "$NEW_TAG" -m "Release $NEW_TAG"
      git push origin "$NEW_TAG"

    - ./gradlew :bom:publishToMavenLocal --no-daemon
    - ./gradlew build --no-daemon
    - ./gradlew publishAllToPrivate --no-daemon

# Publish release on version tags
publish-release:
  stage: publish-release
  only:
    - tags
  script:
    - ./gradlew :bom:publishToMavenLocal --no-daemon
    - ./gradlew build --no-daemon
    - ./gradlew publishAllToPrivate --no-daemon
```

## Air-Gapped Environment Setup

For completely offline environments:

### 1. Mirror Dependencies

First, on a machine with internet access, download all dependencies:

```bash
# Download all dependencies
./gradlew build --refresh-dependencies

# Copy Gradle wrapper and dependencies
tar -czf gradle-dependencies.tar.gz \
  .gradle/wrapper \
  .gradle/caches \
  gradle/wrapper
```

Transfer `gradle-dependencies.tar.gz` to your air-gapped environment.

### 2. Set Up Gradle Init Script

Create `~/.gradle/init.gradle`:

```groovy
allprojects {
    repositories {
        // Only use local and private repositories
        mavenLocal()
        maven {
            url 'https://nexus.internal.company.com/repository/maven-public/'
            credentials {
                username System.getenv('MAVEN_PRIVATE_USERNAME')
                password System.getenv('MAVEN_PRIVATE_PASSWORD')
            }
        }
        // Remove or comment out mavenCentral()
    }
}
```

### 3. Remove Maven Central References

Edit `settings.gradle` or `build.gradle`:

```gradle
// Comment out or remove mavenCentral() from all repositories blocks
repositories {
    mavenLocal()
    maven {
        url findProperty('mavenPrivateUrl')
        credentials {
            username findProperty('mavenPrivateUsername')
            password findProperty('mavenPrivatePassword')
        }
    }
    // mavenCentral() // REMOVED for air-gapped
}
```

## Testing Locally

Before deploying to CI/CD, test locally:

```bash
# 1. Publish BOM locally
./gradlew :bom:publishToMavenLocal

# 2. Build all modules
./gradlew build

# 3. Publish to your private repo (test credentials)
./gradlew publishAllToPrivate

# 4. Verify artifacts in your private repository
curl -u username:password https://nexus.internal.company.com/repository/maven-releases/ai/pipestream/testdata/
```

## Troubleshooting

### Issue: Circular Dependency on BOM

**Solution:** Always run `:bom:publishToMavenLocal` first:
```bash
./gradlew :bom:publishToMavenLocal --no-daemon
./gradlew build --no-daemon
```

### Issue: Cannot Resolve Dependencies

**Solution:** Check your repository configuration and credentials:
```bash
# Test repository access
curl -u username:password https://nexus.internal.company.com/repository/maven-releases/

# Verify gradle.properties
cat ~/.gradle/gradle.properties
```

### Issue: GPG Signing Fails

**Solution:** Export your GPG key and configure properly:
```bash
# Export your key (on machine with key)
gpg --export-secret-keys YOUR_KEY_ID > secring.gpg

# Configure in gradle.properties
signing.keyId=YOUR_KEY_ID
signing.password=YOUR_KEY_PASSWORD
signing.secretKeyRingFile=/path/to/secring.gpg
```

## Summary Checklist

- [ ] Configure `~/.gradle/gradle.properties` with private repository URLs and credentials
- [ ] Update `build.gradle` to add private repository
- [ ] Create `.gitlab-ci.yml` or equivalent CI/CD configuration
- [ ] Test BOM publishing locally: `./gradlew :bom:publishToMavenLocal`
- [ ] Test full build: `./gradlew build`
- [ ] Test publishing: `./gradlew publishAllToPrivate`
- [ ] Verify artifacts appear in private repository
- [ ] Configure CI/CD environment variables/secrets
- [ ] Run CI/CD pipeline and verify

## Quick Start Commands

```bash
# Local development
./gradlew :bom:publishToMavenLocal
./gradlew build
./gradlew publishToMavenLocal

# Publish to private repository
./gradlew :bom:publishToMavenLocal
./gradlew build
./gradlew publishAllToPrivate

# Create release
git tag v0.2.0
git push origin v0.2.0
```

## Additional Resources

- [Gradle Publishing Guide](https://docs.gradle.org/current/userguide/publishing_maven.html)
- [GitLab Package Registry](https://docs.gitlab.com/ee/user/packages/maven_repository/)
- [Nexus Repository Manager](https://help.sonatype.com/repomanager3)
- [Artifactory User Guide](https://jfrog.com/help/r/jfrog-artifactory-documentation)
