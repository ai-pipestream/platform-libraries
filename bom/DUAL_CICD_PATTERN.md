# Dual CI/CD Pattern for Java Services

This document describes the pattern for setting up dual CI/CD pipelines that publish containers to both Gitea and GitHub registries.

## Overview

Each runnable Java service should have:
- `.gitea/workflows/build-and-publish.yml` → publishes to `git.rokkon.com`
- `.github/workflows/build-and-publish.yml` → publishes to `ghcr.io`

## Registry Strategy

| Registry | URL Pattern | Visibility | Authentication |
|----------|-------------|------------|----------------|
| **Gitea** | `git.rokkon.com/io-pipeline/{service}:latest` | Public | `GIT_USER` + `GIT_PAT` |
| **GitHub** | `ghcr.io/io-pipeline/{service}:latest` | Public | `github.actor` + `GITHUB_TOKEN` |

## Workflow Pattern

### Gitea Workflow (`.gitea/workflows/build-and-publish.yml`)

```yaml
name: Build and Publish

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Cache Gradle packages
        uses: actions/cache@v3
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
          restore-keys: |
            ${{ runner.os }}-gradle-

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build and test with Gradle
        env:
          REPOS_PAT: ${{ secrets.REPOS_PAT }}
        run: ./gradlew clean build test --no-daemon

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-results
          path: build/test-results/

      - name: Publish to Reposilite and Gitea
        if: gitea.ref == 'refs/heads/main'
        env:
          REPOS_PAT: ${{ secrets.REPOS_PAT }}
          GIT_USER: ${{ secrets.GIT_USER }}
          GIT_PAT: ${{ secrets.GIT_PAT }}
        run: ./gradlew publish --no-daemon

  docker:
    runs-on: ubuntu-latest
    needs: build
    if: gitea.ref == 'refs/heads/main'

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Cache Gradle packages
        uses: actions/cache@v3
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
          restore-keys: |
            ${{ runner.os }}-gradle-

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Login to Gitea Container Registry
        uses: docker/login-action@v3
        with:
          registry: git.rokkon.com
          username: ${{ secrets.GIT_USER }}
          password: ${{ secrets.GIT_PAT }}

      - name: Build and push Docker image with Quarkus
        env:
          QUARKUS_CONTAINER_IMAGE_USERNAME: ${{ secrets.GIT_USER }}
          QUARKUS_CONTAINER_IMAGE_PASSWORD: ${{ secrets.GIT_PAT }}
          REPOS_PAT: ${{ secrets.REPOS_PAT }}
        run: |
          ./gradlew build \
            -Dquarkus.container-image.build=true \
            -Dquarkus.container-image.push=true \
            -Dquarkus.container-image.additional-tags=${{ gitea.sha }} \
            --no-daemon
```

#### What each step does (Gitea)
- on: Defines when the workflow runs.
  - push/pull_request on main: Build on PRs; publish only on main.
- jobs.build: Compiles and tests the Java project.
  - Checkout code: Fetches repository contents.
  - Set up JDK 21: Installs the Temurin JDK.
  - Cache Gradle packages: Speeds builds by caching Gradle wrapper and dependencies.
  - Grant execute permission for gradlew: Ensures the wrapper can run on Linux runners.
  - Build and test with Gradle: Runs clean build and unit tests. Uses REPOS_PAT to access private Maven repositories if required.
  - Upload test results (always): Publishes test reports as an artifact even if the build fails.
  - Publish to Reposilite and Gitea (main only): On main, runs ./gradlew publish to push libraries to the internal Maven (Reposilite) and prepares metadata for container build. Authentication uses REPOS_PAT, GIT_USER, and GIT_PAT.
- jobs.docker: Builds and pushes the container image to Gitea (main only) after build completes.
  - Checkout code/JDK/Cache/gradlew: Repeats essentials in this job’s fresh runner.
  - Set up Docker Buildx: Enables advanced, cache-aware Docker builds.
  - Login to Gitea Container Registry: Authenticates to git.rokkon.com registry with GIT_USER/GIT_PAT.
  - Build and push Docker image with Quarkus: Uses Quarkus container-image extension via Gradle to build and push. Adds an extra tag using the commit SHA (gitea.sha). Secrets are injected through QUARKUS_CONTAINER_IMAGE_* env vars.

Notes:
- Conditions: The if: gitea.ref == 'refs/heads/main' guard ensures containers only push from main.
- Secrets needed in Gitea: GIT_USER, GIT_PAT (registry login), REPOS_PAT (private Maven access).

### GitHub Workflow (`.github/workflows/build-and-publish.yml`)

```yaml
name: Build and Publish

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Cache Gradle packages
        uses: actions/cache@v3
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
          restore-keys: |
            ${{ runner.os }}-gradle-

      - name: Override Gradle wrapper distributionUrl
        run: |
          sed -i 's|distributionUrl=https\\://maven.rokkon.com/gradle-distributions/|distributionUrl=https\\://services.gradle.org/distributions/|g' gradle/wrapper/gradle-wrapper.properties
          echo "Updated gradle-wrapper.properties:"
          cat gradle/wrapper/gradle-wrapper.properties | grep distributionUrl

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build and test with Gradle
        run: ./gradlew clean build test --no-daemon

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-results
          path: build/test-results/

  docker:
    runs-on: ubuntu-latest
    needs: build
    if: github.ref == 'refs/heads/main'

    permissions:
      contents: read
      packages: write

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Cache Gradle packages
        uses: actions/cache@v3
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
          restore-keys: |
            ${{ runner.os }}-gradle-

      - name: Override Gradle wrapper distributionUrl
        run: |
          sed -i 's|distributionUrl=https\\://maven.rokkon.com/gradle-distributions/|distributionUrl=https\\://services.gradle.org/distributions/|g' gradle/wrapper/gradle-wrapper.properties
          echo "Updated gradle-wrapper.properties:"
          cat gradle/wrapper/gradle-wrapper.properties | grep distributionUrl

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Login to GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push Docker image with Quarkus
        env:
          QUARKUS_CONTAINER_IMAGE_REGISTRY: ghcr.io
          QUARKUS_CONTAINER_IMAGE_GROUP: io-pipeline
          QUARKUS_CONTAINER_IMAGE_USERNAME: ${{ github.actor }}
          QUARKUS_CONTAINER_IMAGE_PASSWORD: ${{ secrets.GITHUB_TOKEN }}
        run: |
          ./gradlew build \
            -Dquarkus.container-image.build=true \
            -Dquarkus.container-image.push=true \
            -Dquarkus.container-image.registry=ghcr.io \
            -Dquarkus.container-image.group=io-pipeline \
            -Dquarkus.container-image.additional-tags=${{ github.sha }} \
            --no-daemon
```

#### What each step does (GitHub)
- on: Defines triggers. Same as Gitea: build for PRs and pushes; container publish only from main.
- jobs.build: Validates the codebase.
  - Checkout code: Fetches repository contents.
  - Set up JDK 21: Installs Temurin JDK.
  - Cache Gradle packages: Caches Gradle wrapper and deps for faster builds.
  - Override Gradle wrapper distributionUrl: Rewrites gradle-wrapper.properties to use public Gradle distributions on GitHub (cannot access internal mirror). Prints the updated URL for auditability.
  - Grant execute permission for gradlew: Ensures wrapper runs on Linux.
  - Build and test with Gradle: Runs clean build and unit tests against public repos. No private REPOS_PAT used.
  - Upload test results (always): Publishes test reports as artifact even on failure.
- jobs.docker: Builds and pushes the container to GitHub Container Registry (ghcr.io) from main only.
  - permissions: packages: write is required to push to GHCR; contents: read for checkout.
  - Checkout/JDK/Cache/gradlew: Standard setup on a fresh runner.
  - Override Gradle wrapper distributionUrl: Same reason as in build job—ensure public Gradle binary.
  - Set up Docker Buildx: Enables modern Docker builds.
  - Login to GitHub Container Registry: Authenticates using the GitHub Actions token.
  - Build and push Docker image with Quarkus: Uses Quarkus container-image extension to build/push to ghcr.io/io-pipeline. Adds commit SHA tag (github.sha). Registry and group are explicitly set via -Dquarkus.container-image.* properties.

Notes:
- Conditions: if: github.ref == 'refs/heads/main' restricts publishing to main branch.
- Secrets/Context: Uses built-in github.actor and GITHUB_TOKEN; no extra secrets needed for public dependencies.

## Key Differences Between Workflows

| Aspect | Gitea | GitHub |
|--------|-------|--------|
| **Context variable** | `gitea.ref`, `gitea.sha` | `github.ref`, `github.sha` |
| **Registry** | `git.rokkon.com` (default) | `ghcr.io` (must specify) |
| **Auth secrets** | `GIT_USER`, `GIT_PAT` | `github.actor`, `GITHUB_TOKEN` |
| **Build deps** | `REPOS_PAT` (private Maven) | None (uses Maven Central) |
| **Permissions** | Not needed | `packages: write` required |
| **Registry props** | Not needed (default) | Must set registry + group |
| **Publish step** | Includes `./gradlew publish` | No publish (public Maven only) |
| **Artifact action** | `upload-artifact@v3` (GHES) | `upload-artifact@v4` (required) |

## Step-by-Step Application

For each service in the list:

### 1. Check existing workflows
```bash
cd /home/krickert/IdeaProjects/gitea/{service}
ls -la .gitea/workflows/
ls -la .github/workflows/
```

### 2. If Gitea workflow exists but GitHub doesn't
```bash
# Create GitHub workflows directory
mkdir -p .github/workflows

# Copy the GitHub workflow template (adjust service name if needed)
# Use the template above
```

### 3. Commit and push
```bash
git add .github/workflows/build-and-publish.yml
git commit -m "Add GitHub Actions workflow for ghcr.io publishing"
git push origin main
```

### 4. Verify
- Check Gitea Actions: `https://git.rokkon.com/io-pipeline/{service}/actions`
- Wait for GitHub sync (auto-mirrors from Gitea)
- Check GitHub Actions: `https://github.com/io-pipeline/{service}/actions`
- Check packages are public:
  - Gitea: `https://git.rokkon.com/io-pipeline/-/packages?type=container`
  - GitHub: `https://github.com/orgs/io-pipeline/packages?ecosystem=container`

## Services to Process

### ✅ Completed
1. platform-shell (Node.js - different pattern)
2. platform-registration-service (Java - baseline)

### 🔲 Remaining (14 services)
3. account-service
4. connector-admin
5. connector-intake-service
6. mapping-service
7. module-chunker
8. module-echo
9. module-embedder
10. module-opensearch-sink
11. module-parser
12. module-pipeline-probe
13. module-proxy
14. opensearch-manager
15. pipestream-engine
16. repository-service (needs Dockerfile first)

## Common Issues & Solutions

### Issue: Container marked as private
**Solution**: Update package visibility on both platforms:
- Gitea: Package settings → Change visibility → Public
- GitHub: Package settings → Change visibility → Public

### Issue: Gitea workflow fails with "not currently supported on GHES"
**Solution**: Gitea uses GHES (GitHub Enterprise Server) compatibility mode which doesn't support artifact v4. Use `upload-artifact@v3` for Gitea workflows, `upload-artifact@v4` for GitHub workflows.

### Issue: GitHub workflow fails with 403 on ghcr.io
**Solution**: Ensure `permissions: packages: write` is set in the docker job

### Issue: Gradle can't find dependencies on GitHub
**Solution**: GitHub uses public Maven Central, private deps won't work. Only Gitea workflow can publish artifacts.

### Issue: Wrong container registry
**Solution**: For GitHub, explicitly set:
- `-Dquarkus.container-image.registry=ghcr.io`
- `-Dquarkus.container-image.group=io-pipeline`

## Notes

- **Gitea is primary**: Code changes push to Gitea first
- **Auto-mirror**: Gitea automatically syncs to GitHub
- **Both workflows run independently**: Each platform builds its own containers
- **Tag strategy**: Both use `latest` + commit SHA tags
- **No cross-registry references**: Each platform is self-contained

## Reference

**Completed example**: `platform-registration-service`
- Gitea workflow: `.gitea/workflows/build-and-publish.yml`
- GitHub workflow: `.github/workflows/build-and-publish.yml`
- Gitea Actions: https://git.rokkon.com/io-pipeline/platform-registration-service/actions
- GitHub Actions: https://github.com/io-pipeline/platform-registration-service/actions (when synced)
