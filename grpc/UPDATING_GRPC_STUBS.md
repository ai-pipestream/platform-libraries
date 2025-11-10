# Updating gRPC Stubs - Complete Guide

This guide covers the complete workflow for updating gRPC proto definitions and propagating changes through the entire platform.

## Overview

The AI Pipestream platform uses shared gRPC stubs that are consumed by:
- **Backend services** (Java/Quarkus) - via Maven local/Central
- **Web-proxy backend** (TypeScript/Node.js) - via npm
- **Frontend** (Vue/TypeScript) - via npm

When you modify proto definitions, you need to regenerate stubs and update all consuming projects.

## Complete Workflow

### Step 1: Update Proto Definitions

Edit proto files in `platform-libraries/grpc/grpc-stubs/src/main/proto/`

**Example: Adding a new streaming RPC**

```protobuf
service PlatformRegistration {
  // Existing unary call
  rpc ListModules(google.protobuf.Empty) returns (ModuleListResponse);

  // New streaming call
  rpc WatchModules(google.protobuf.Empty) returns (stream ModuleListResponse);
}
```

**Common changes:**
- Adding new RPC methods to services
- Adding new message types
- Modifying existing messages (careful with breaking changes!)
- Adding new services

### Step 2: Regenerate and Publish Stubs

Run the automated regeneration script:

```bash
cd /home/krickert/IdeaProjects/ai-pipestream/platform-libraries
./scripts/regenerate-stubs.sh
```

**What this script does:**

1. **Builds grpc-google-descriptor**
   - Contains base Google protobuf types (Timestamp, Empty, etc.)
   - Publishes to Maven local: `~/.m2/repository/ai/pipestream/grpc-google-descriptor/`

2. **Regenerates Java stubs**
   - Runs Quarkus code generation from proto files
   - Generates Mutiny-based gRPC stubs
   - Compiles and packages JAR
   - Publishes to Maven local: `~/.m2/repository/ai/pipestream/grpc-stubs/`

3. **Regenerates TypeScript stubs**
   - Uses `buf generate` to create TypeScript from proto
   - Compiles TypeScript (generates `.d.ts` files)
   - Publishes to npm with auto-incremented alpha version
   - Format: `0.1.6-alpha.YYYYMMDDHHMMSS`

**Script output:**
```
✨ Stub regeneration complete!

Generated and published:
  Java (Maven local):       .../grpc-stubs/build/classes/java/quarkus-generated-sources/grpc/
  TypeScript (npm):         @ai-pipestream/grpc-stubs@0.1.6-alpha.20251110123501

Next steps:
  1. For Java services: Rebuild to pick up updated stubs
  2. For frontend: Update to new alpha version
     - cd platform-frontend && pnpm add -w @ai-pipestream/grpc-stubs@0.1.6-alpha.20251110123501
     - cd platform-frontend && ./scripts/nuclear-clean.sh

Published version: 0.1.6-alpha.20251110123501
```

**IMPORTANT:** Copy the published version number for the next steps!

### Step 3: Update Backend Services (Java)

#### 3a. Implement New/Changed RPCs

For services that use the updated stubs (e.g., `platform-registration-service`):

**Add method to gRPC service class:**

```java
// platform-registration-service/src/main/java/ai/pipestream/registration/grpc/PlatformRegistrationService.java

@Override
public Multi<ModuleListResponse> watchModules(Empty request) {
    LOG.info("Received request to watch modules for real-time updates");
    return discoveryHandler.watchModules();
}
```

**Implement the handler:**

```java
// platform-registration-service/src/main/java/ai/pipestream/registration/handlers/ServiceDiscoveryHandler.java

public Multi<ModuleListResponse> watchModules() {
    LOG.info("Starting module watch stream");

    // Send initial list immediately
    Multi<ModuleListResponse> initialList = Multi.createFrom().uni(listModules())
        .onItem().invoke(response ->
            LOG.infof("Sending initial module list with %d modules", response.getTotalCount())
        );

    // Poll for changes
    Multi<ModuleListResponse> updates = Multi.createFrom().ticks().every(Duration.ofSeconds(2))
        .onItem().transformToUniAndConcatenate(tick -> listModules())
        .onFailure().recoverWithItem(throwable -> {
            LOG.error("Error in module watch", throwable);
            return buildEmptyModuleList();
        });

    // Combine streams
    return Multi.createBy().concatenating()
        .streams(initialList, updates)
        .onCompletion().invoke(() -> LOG.info("Module watch stream completed"))
        .onCancellation().invoke(() -> LOG.info("Module watch cancelled"));
}
```

#### 3b. Rebuild the Service

```bash
cd /home/krickert/IdeaProjects/ai-pipestream/platform-registration-service
./gradlew clean build -x test
```

**Note:** The service automatically picks up the updated grpc-stubs from Maven local (we configured `settings.gradle` to allow SNAPSHOTs from Maven local).

#### 3c. Restart the Service

```bash
# If running in IDE: Stop and restart
# If running via script:
./scripts/start-platform-registration.sh
```

### Step 4: Update Web-Proxy Backend

The web-proxy in `platform-frontend/apps/platform-shell` proxies gRPC calls from the browser to backend services.

#### 4a. Update grpc-stubs Version

Edit `apps/platform-shell/package.json`:

```json
{
  "dependencies": {
    "@ai-pipestream/grpc-stubs": "0.1.6-alpha.20251110123501"
  }
}
```

Use the version from Step 2 output.

#### 4b. Add Proxy Methods (if adding new RPCs)

Edit `apps/platform-shell/src/routes/connectRoutes.ts`:

```typescript
router.service(PlatformRegistration, {
  // ... existing methods ...

  async *watchModules(req: any) {
    console.log("[Connect] Proxying watchModules to platform-registration-service");
    const client = createClient(PlatformRegistration, registrationTransport);
    for await (const event of client.watchModules(req)) {
      yield event;
    }
  }
});
```

**For streaming methods:**
- Use `async *` (async generator)
- Use `for await` to consume backend stream
- Use `yield` to pass events to client

**For unary methods:**
- Just `return client.methodName(req)`

#### 4c. Install and Restart

```bash
cd /home/krickert/IdeaProjects/ai-pipestream/platform-frontend
pnpm install

# Restart backend (if using start script, it will prompt to kill existing)
./scripts/start-backend.sh
```

### Step 5: Update Frontend (Vue/TypeScript)

#### 5a. Update grpc-stubs Version

Edit `apps/platform-shell/ui/package.json`:

```json
{
  "dependencies": {
    "@ai-pipestream/grpc-stubs": "0.1.6-alpha.20251110123501"
  }
}
```

#### 5b. Implement Client Code

**For stores (Pinia):**

```typescript
// apps/platform-shell/ui/src/stores/serviceRegistry.ts

import { createClient, ConnectError, Code } from '@connectrpc/connect'
import { createConnectTransport } from '@connectrpc/connect-web'
import { PlatformRegistration } from '@ai-pipestream/grpc-stubs/dist/registration/platform_registration_pb.ts'

export const useServiceRegistryStore = defineStore('serviceRegistry', () => {
  const availableModules = ref<Set<string>>(new Set())
  let moduleAbortController: AbortController | null = null
  let isUnmounted = false

  const startModuleStream = async (): Promise<void> => {
    if (isUnmounted) return

    // Cancel previous stream
    if (moduleAbortController) {
      moduleAbortController.abort()
    }
    moduleAbortController = new AbortController()

    try {
      console.log('[ServiceRegistry] Starting module stream...')

      const transport = createConnectTransport({
        baseUrl: window.location.origin,
        useBinaryFormat: true  // ALWAYS use binary
      })

      const client = createClient(PlatformRegistration, transport)

      for await (const response of client.watchModules({}, {
        signal: moduleAbortController.signal,
        timeoutMs: undefined  // No timeout for streaming
      })) {
        const modules = new Set<string>()
        for (const details of response.modules) {
          if (details.isHealthy) {
            modules.add(details.moduleName)
          }
        }

        // Only update if data actually changed (deduplication)
        const current = availableModules.value
        const hasChanged =
          modules.size !== current.size ||
          Array.from(modules).some(m => !current.has(m))

        if (hasChanged) {
          availableModules.value = modules
          console.log('[ServiceRegistry] Modules updated:', Array.from(modules))
        }
      }

      console.log('[ServiceRegistry] Module stream ended')
    } catch (error: any) {
      // Don't log canceled errors (expected during cleanup/refresh)
      if (error instanceof ConnectError && error.code === Code.Canceled) {
        console.log('[ServiceRegistry] Module stream canceled')
        return
      }

      console.error('[ServiceRegistry] Module stream error:', error)

      // Retry with exponential backoff
      if (!isUnmounted) {
        const delay = 5000 // 5 seconds
        console.log(`[ServiceRegistry] Retrying module stream in ${delay}ms...`)
        setTimeout(() => startModuleStream(), delay)
      }
    }
  }

  const cleanup = () => {
    isUnmounted = true
    if (moduleAbortController) {
      moduleAbortController.abort()
      moduleAbortController = null
    }
  }

  // Start stream on initialization
  startModuleStream()

  return {
    availableModules,
    cleanup
  }
})
```

#### 5c. Nuclear Clean (Critical for Vite!)

```bash
cd /home/krickert/IdeaProjects/ai-pipestream/platform-frontend
./scripts/nuclear-clean.sh
```

**Why this is necessary:**
- Vite aggressively caches dependencies
- Even after `pnpm install`, Vite may serve stale files
- Nuclear clean wipes ALL caches: node_modules, lockfiles, Vite cache
- Fresh install ensures browser gets the new package

#### 5d. Restart Dev Server

```bash
# If using the combined script:
./scripts/start-platform-shell.sh

# Or separately:
./scripts/start-frontend.sh
```

### Step 6: Verify Everything Works

#### 6a. Check Backend with grpcurl

```bash
# List all methods
grpcurl -plaintext localhost:38101 list ai.pipestream.platform.registration.PlatformRegistration

# Should show:
# ai.pipestream.platform.registration.PlatformRegistration.WatchModules

# Test the streaming call
grpcurl -plaintext -d '{}' localhost:38101 \
  ai.pipestream.platform.registration.PlatformRegistration/WatchModules

# Should stream JSON responses (Ctrl+C to stop)
```

#### 6b. Check Browser Console

Open browser DevTools console and verify:

```
[ServiceRegistry] Loaded PlatformRegistration service descriptor: {...}
[ServiceRegistry] Service methods: (11) [...]
[ServiceRegistry] Client methods: (11) ['...', 'watchModules']
[ServiceRegistry] Has watchModules? true function
[ServiceRegistry] Starting module stream...
[ServiceRegistry] Modules updated: [...]
```

**Red flags:**
- Method count wrong (e.g., 10 instead of 11) → Vite cache issue, run nuclear clean again
- `Has watchModules? false` → Wrong grpc-stubs version loaded
- 404 errors → Check backend is running and web-proxy has proxy method

#### 6c. Check Network Tab

- Filter for `WatchModules`
- Request should show as "Pending" while streaming
- Should NOT show 404 or other errors

### Step 7: Promote to Stable (Production)

When ready to release (not for every development iteration):

#### 7a. Update Version Numbers

In `platform-libraries/grpc/node/package.json`:

```json
{
  "version": "0.1.6"  // Remove -alpha suffix
}
```

#### 7b. Merge to Main

```bash
cd /home/krickert/IdeaProjects/ai-pipestream/platform-libraries
git add grpc/
git commit -m "Add WatchModules streaming RPC to platform registration"
git push origin main
```

#### 7c. CI/CD Auto-Publishes

The CI/CD workflows automatically publish stable versions:

**Java stubs:**
- Workflow: `.github/workflows/snapshot.yml`
- Publishes to Maven Central Snapshots
- Version: `0.1.2-SNAPSHOT`

**TypeScript stubs:**
- Workflow: `.github/workflows/npm-publish.yml`
- Publishes to npmjs.org with tag `latest`
- Version: `0.1.6` (from package.json)

#### 7d. Update Production Dependencies

Update consuming projects to use stable versions:

```json
// platform-frontend/apps/platform-shell/package.json
{
  "dependencies": {
    "@ai-pipestream/grpc-stubs": "0.1.6"  // Stable version
  }
}
```

## Version Management

### Development (Alpha Versions)

**Format:** `0.1.6-alpha.YYYYMMDDHHMMSS`

**Characteristics:**
- Published with `--tag alpha` (not installed by default)
- Auto-incremented timestamp for uniqueness
- Used during development and testing
- Can be cleaned up or deprecated after testing

**Publishing:**
- Automatic via `regenerate-stubs.sh` script
- Manual: `cd grpc/node && npm version 0.1.6-alpha.X --no-git-tag-version && npm publish --tag alpha`

### Production (Stable Versions)

**Java format:** `0.1.2-SNAPSHOT` (managed by BOM)
**TypeScript format:** `0.1.6` (from package.json)

**Publishing:**
- Automatic via CI/CD on main branch
- Requires merging changes to main

## Common Workflows

### Adding a New RPC Method

1. Edit proto file → add `rpc MethodName(...) returns (...)`
2. Run `./scripts/regenerate-stubs.sh`
3. Note the alpha version from output
4. Implement backend handler
5. Rebuild backend service
6. Update web-proxy package.json + add proxy method
7. Restart web-proxy
8. Update frontend package.json + implement client
9. Run `nuclear-clean.sh` + restart frontend
10. Test with grpcurl and browser

### Adding a New Message Type

1. Edit proto file → add `message NewType { ... }`
2. Run `./scripts/regenerate-stubs.sh`
3. Update package.json files with new alpha version
4. Run `pnpm install` in platform-frontend
5. Use the new types in code
6. Run `nuclear-clean.sh` for frontend
7. Restart services

### Modifying Existing Messages (Breaking Changes)

**WARNING:** Breaking changes require version bump!

1. Update proto with new fields/structure
2. Bump version in `grpc/node/package.json` (e.g., `0.1.6` → `0.2.0`)
3. Run `./scripts/regenerate-stubs.sh`
4. Update ALL consuming projects to handle new structure
5. Test thoroughly before promoting to stable

## File Locations

### Source Files
- **Proto definitions**: `platform-libraries/grpc/grpc-stubs/src/main/proto/`
- **Regeneration script**: `platform-libraries/scripts/regenerate-stubs.sh`

### Generated Files
- **Java stubs**: `platform-libraries/grpc/grpc-stubs/build/classes/java/quarkus-generated-sources/grpc/`
- **TypeScript stubs**: `platform-libraries/grpc/node/dist/`

### Published Artifacts
- **Java (Maven local)**: `~/.m2/repository/ai/pipestream/grpc-stubs/`
- **TypeScript (npm)**: https://www.npmjs.com/package/@ai-pipestream/grpc-stubs

### Consuming Projects
- **Backend services**: Use Maven dependency `ai.pipestream:grpc-stubs`
- **Web-proxy backend**: `platform-frontend/apps/platform-shell/package.json`
- **Frontend UI**: `platform-frontend/apps/platform-shell/ui/package.json`

## Dependency Configuration

### Java Services (Maven local enabled for SNAPSHOTs)

In `settings.gradle`:

```gradle
repositories {
    // Maven Local for development (now includes SNAPSHOTs)
    mavenLocal {
        content {
            includeGroupByRegex "ai\\.pipestream(\\..*)?"
        }
    }

    mavenCentral()

    // Maven Central Snapshots
    maven {
        url = uri('https://central.sonatype.com/repository/maven-snapshots/')
        mavenContent {
            snapshotsOnly()
        }
    }
}
```

**Key change:** Removed `releasesOnly()` to allow local SNAPSHOT testing.

### TypeScript Projects (npm)

All three package.json files must use the same alpha version:

1. `platform-frontend/package.json` (workspace root)
2. `platform-frontend/apps/platform-shell/package.json` (web-proxy backend)
3. `platform-frontend/apps/platform-shell/ui/package.json` (Vue frontend)

## Troubleshooting

### Issue: Java Compilation Errors

**Symptom:** `method does not override or implement a method from a supertype`

**Solutions:**
1. Verify stubs were published to Maven local: `ls ~/.m2/repository/ai/pipestream/grpc-stubs/0.1.2-SNAPSHOT/`
2. Check the JAR has the method: `javap -cp ~/.m2/repository/ai/pipestream/grpc-stubs/0.1.2-SNAPSHOT/grpc-stubs-0.1.2-SNAPSHOT.jar ai.pipestream.platform.registration.MutinyPlatformRegistrationGrpc\$PlatformRegistrationImplBase | grep methodName`
3. Clean Gradle cache: `cd service && rm -rf .gradle build && ./gradlew clean build`

### Issue: TypeScript Method Not Found

**Symptom:** `client.methodName is not a function`

**Solutions:**
1. Check browser console for method count (should match proto definition)
2. Verify package.json has correct alpha version
3. Run `./scripts/nuclear-clean.sh` to clear ALL caches
4. Hard refresh browser (Ctrl+Shift+R) with DevTools cache disabled
5. Check Sources tab in DevTools to see actual loaded file

### Issue: 404 on RPC Call

**Symptom:** `POST /ai.pipestream.service.Name/Method` returns 404

**Check chain:**
1. **Backend service** has method: `grpcurl -plaintext localhost:38101 list Service`
2. **Web-proxy** has proxy method in `connectRoutes.ts`
3. **Vite proxy** configured in `ui/vite.config.ts` for path pattern
4. All three tiers use same grpc-stubs version

### Issue: Stream Connects but No Data

**Check:**
1. Backend logs show stream started
2. Test directly: `grpcurl -plaintext -d '{}' localhost:38101 Service/Method`
3. Backend implementation actually emits data (not just empty stream)

### Issue: "FROM-CACHE" in Gradle Build

**Symptom:** Stubs not regenerating despite proto changes

**Solution:**
```bash
cd platform-libraries
./gradlew :grpc:grpc-stubs:clean :grpc:grpc-stubs:build --rerun-tasks --no-build-cache
./gradlew :grpc:grpc-stubs:publishToMavenLocal
```

### Issue: Vite Serves Wrong Version

**Symptom:** Browser loads old grpc-stubs despite updated package.json

**Nuclear option:**
```bash
cd platform-frontend
./scripts/nuclear-clean.sh
```

This deletes:
- All `node_modules`
- All lockfiles
- pnpm store cache
- All Vite caches
- All build outputs

Then does fresh `pnpm install`.

## Best Practices

### Proto Design

1. **Use streaming for real-time data** - Don't poll if you can stream
2. **Reuse message types** - Define common types once
3. **Document everything** - Add comments to proto files
4. **Avoid breaking changes** - Add new fields with `optional` or use `oneof`

### Version Strategy

1. **Development**: Use alpha versions for rapid iteration
2. **Testing**: Keep alpha versions for a few days, then deprecate
3. **Production**: Only promote well-tested changes to stable
4. **Cleanup**: Deprecate old alphas: `npm deprecate @ai-pipestream/grpc-stubs@0.1.6-alpha.0 "Testing version"`

### Dependency Updates

1. **Keep versions in sync** - All three package.json files should match
2. **Test locally first** - Use alpha versions before promoting to stable
3. **Update incrementally** - Don't batch multiple breaking changes

### Caching

1. **Always run nuclear-clean** after updating grpc-stubs version
2. **Disable browser cache** during development (DevTools → Network → Disable cache)
3. **Use hard refresh** (Ctrl+Shift+R) after updates

## Scripts and Automation

### Key Scripts

| Script | Location | Purpose |
|--------|----------|---------|
| `regenerate-stubs.sh` | platform-libraries/scripts/ | Regenerates and publishes all stubs |
| `nuclear-clean.sh` | platform-frontend/scripts/ | Clears all caches and reinstalls |
| `start-platform-shell.sh` | platform-frontend/scripts/ | Starts backend + frontend together |

### CI/CD Workflows

| Workflow | Trigger | Publishes |
|----------|---------|-----------|
| `npm-publish.yml` | Push to main or dev branches | npm alpha or stable |
| `snapshot.yml` | Push to main | Maven Central snapshots |

## Quick Reference

### After Editing Proto Files

```bash
# 1. Regenerate
cd platform-libraries
./scripts/regenerate-stubs.sh
# Copy the alpha version from output: 0.1.6-alpha.YYYYMMDDHHMMSS

# 2. Update backend service
cd ../platform-registration-service
# Edit Java files to implement new methods
./gradlew clean build -x test
./scripts/start-platform-registration.sh

# 3. Update web-proxy
cd ../platform-frontend/apps/platform-shell
# Edit package.json with new alpha version
# Edit connectRoutes.ts if adding new proxy methods
cd ../..
pnpm install
./scripts/start-backend.sh

# 4. Update frontend
cd apps/platform-shell/ui
# Edit package.json with new alpha version
# Implement client code
cd ../../..
./scripts/nuclear-clean.sh
./scripts/start-frontend.sh
```

### Checking Versions

```bash
# Check Java stubs in Maven local
ls ~/.m2/repository/ai/pipestream/grpc-stubs/

# Check npm versions
pnpm view @ai-pipestream/grpc-stubs versions --json

# Check what's installed
pnpm list @ai-pipestream/grpc-stubs
```

### Testing

```bash
# Test backend directly
grpcurl -plaintext localhost:38101 list Service.Name
grpcurl -plaintext -d '{}' localhost:38101 Service.Name/Method

# Test through web-proxy
grpcurl -plaintext localhost:38106 list Service.Name

# Check running services
lsof -i :38101  # Platform registration
lsof -i :38106  # Web-proxy backend
lsof -i :33000  # Vite frontend
```

## Related Documentation

- **Streaming Guide**: `grpc/STREAMING_GUIDE.md` - Deep dive into streaming RPCs
- **README**: `grpc/grpc-stubs/README.md` - About the grpc-stubs project
- **Platform Frontend README**: `platform-frontend/README.md` - Configuration and deployment

## Support

If you run into issues:
1. Check this guide's Troubleshooting section
2. Verify all version numbers match
3. Run nuclear-clean and try again
4. Test each tier independently (backend → web-proxy → frontend)
5. Check CI/CD workflows for production publishing
