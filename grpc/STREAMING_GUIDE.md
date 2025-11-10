# gRPC Streaming Call Guide

This guide explains how to add streaming gRPC calls to the AI Pipestream platform.

## What is Streaming?

**Streaming gRPC** allows the server to push multiple messages to a client over a single, persistent connection instead of requiring the client to repeatedly poll for updates.

### Benefits of Streaming vs Polling

| Streaming | Polling |
|-----------|---------|
| ✅ Real-time updates (instant) | ❌ Delayed updates (poll interval) |
| ✅ Lower server load (1 connection) | ❌ Higher server load (N requests) |
| ✅ Lower bandwidth (only changes) | ❌ Higher bandwidth (full state each time) |
| ✅ Lower latency | ❌ Higher latency |
| ✅ Efficient (events pushed) | ❌ Wasteful (constant checking) |

## Types of Streaming

1. **Server Streaming** (used in this platform): Server sends multiple responses to one client request
   - Example: `WatchServices`, `WatchModules`, `WatchHealth`
2. **Client Streaming**: Client sends multiple requests, server sends one response
3. **Bidirectional Streaming**: Both client and server send streams

## Complete Workflow: Adding a Streaming RPC

### Step 1: Define the Proto

Edit the proto file in `grpc/grpc-stubs/src/main/proto/`:

```protobuf
service PlatformRegistration {
  // Existing unary calls
  rpc ListModules(google.protobuf.Empty) returns (ModuleListResponse);

  // New streaming call - note the "stream" keyword before the response
  rpc WatchModules(google.protobuf.Empty) returns (stream ModuleListResponse);
}
```

**Key points:**
- Use `stream` keyword before the return type
- Reuse existing message types when possible
- Add clear documentation comments

### Step 2: Regenerate Stubs

Run the regeneration script from `platform-libraries`:

```bash
cd /path/to/platform-libraries
./scripts/regenerate-stubs.sh
```

This will:
1. Build `grpc-google-descriptor` (base types)
2. Generate Java stubs and publish to Maven local
3. Generate TypeScript stubs and publish to npm with auto-incremented alpha version

The script outputs the new version number at the end.

### Step 3: Implement Backend (Java/Quarkus)

#### 3a. Update the gRPC Service

In `PlatformRegistrationService.java`:

```java
@Override
public Multi<ModuleListResponse> watchModules(Empty request) {
    LOG.info("Received request to watch modules for real-time updates");
    return discoveryHandler.watchModules();
}
```

**Key points:**
- Return type must be `Multi<ResponseType>` for server streaming
- Use `Multi` from `io.smallrye.mutiny`

#### 3b. Implement the Handler

In `ServiceDiscoveryHandler.java`:

```java
public Multi<ModuleListResponse> watchModules() {
    LOG.info("Starting module watch stream");

    // Send initial list immediately
    Multi<ModuleListResponse> initialList = Multi.createFrom().uni(listModules())
        .onItem().invoke(response ->
            LOG.infof("Sending initial module list with %d modules", response.getTotalCount())
        );

    // Then poll for changes every 2 seconds
    Multi<ModuleListResponse> updates = Multi.createFrom().ticks().every(Duration.ofSeconds(2))
        .onItem().transformToUniAndConcatenate(tick -> listModules())
        .onItem().invoke(response ->
            LOG.debugf("Module watch update: %d modules", response.getTotalCount())
        )
        .onFailure().invoke(throwable ->
            LOG.error("Error during module watch", throwable)
        )
        .onFailure().recoverWithItem(throwable -> {
            LOG.error("Recovering from error in module watch", throwable);
            return buildEmptyModuleList();
        });

    // Combine initial list with ongoing updates
    return Multi.createBy().concatenating()
        .streams(initialList, updates)
        .onCompletion().invoke(() -> LOG.info("Module watch stream completed"))
        .onCancellation().invoke(() -> LOG.info("Module watch stream cancelled by client"));
}
```

**Key patterns:**
- Send initial state immediately (improves UX)
- Use `Multi.createFrom().ticks()` for periodic updates
- Handle errors gracefully with `onFailure().recoverWithItem()`
- Log lifecycle events (start, complete, cancel)

#### 3c. Rebuild the Backend Service

```bash
cd /path/to/platform-registration-service
./gradlew clean build -x test
```

### Step 4: Update Web-Proxy Backend

The web-proxy in `platform-frontend/apps/platform-shell` needs to proxy the new method.

#### 4a. Update grpc-stubs Dependency

Edit `apps/platform-shell/package.json`:

```json
"@ai-pipestream/grpc-stubs": "0.1.6-alpha.XXXXXXXX"
```

Use the version from Step 2 output.

#### 4b. Add Proxy Method

In `src/routes/connectRoutes.ts`, add the streaming proxy:

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

**Key points:**
- Use `async *` for generator function
- Use `for await` to consume the stream
- Use `yield` to pass events to the client

#### 4c. Run pnpm install and Restart

```bash
cd /path/to/platform-frontend
pnpm install
# Restart the backend
```

### Step 5: Update Frontend (Vue/TypeScript)

#### 5a. Update grpc-stubs Dependency

Edit `apps/platform-shell/ui/package.json`:

```json
"@ai-pipestream/grpc-stubs": "0.1.6-alpha.XXXXXXXX"
```

#### 5b. Implement Streaming Client

In your Vue store or composable:

```typescript
import { createClient, ConnectError, Code } from '@connectrpc/connect'
import { createConnectTransport } from '@connectrpc/connect-web'
import { PlatformRegistration } from '@ai-pipestream/grpc-stubs/dist/registration/platform_registration_pb.ts'

// Create transport
const transport = createConnectTransport({
  baseUrl: window.location.origin,
  useBinaryFormat: true  // Always use binary for performance
})

// Create client
const client = createClient(PlatformRegistration, transport)

// Start the stream
const startModuleStream = async (): Promise<void> => {
  const abortController = new AbortController()

  try {
    console.log('Starting module stream...')

    for await (const response of client.watchModules({}, {
      signal: abortController.signal,
      timeoutMs: undefined  // No timeout for streaming
    })) {
      // Process each update
      const modules = new Set<string>()
      for (const details of response.modules) {
        if (details.isHealthy) {
          modules.add(details.moduleName)
        }
      }

      // Update your reactive state
      availableModules.value = modules
      console.log('Modules updated:', Array.from(modules))
    }

    console.log('Module stream ended')
  } catch (error: any) {
    // Don't log canceled errors (expected during cleanup)
    if (error instanceof ConnectError && error.code === Code.Canceled) {
      console.log('Module stream canceled')
      return
    }

    console.error('Module stream error:', error)

    // Implement retry logic with exponential backoff
    const delay = 5000 // 5 seconds
    setTimeout(() => startModuleStream(), delay)
  }
}

// Cleanup on unmount
onUnmounted(() => {
  abortController.abort()
})
```

**Key patterns:**
- Use `AbortController` for proper cancellation
- Handle `Code.Canceled` errors separately (they're expected)
- Implement retry logic with backoff
- Always use `useBinaryFormat: true`
- Set `timeoutMs: undefined` for streaming

#### 5c. Nuclear Clean and Restart

```bash
cd /path/to/platform-frontend
./scripts/nuclear-clean.sh
# This ensures Vite picks up the new package version
```

## Common Issues and Solutions

### Issue: Vite Caching Old Version

**Symptoms:** Client methods don't include the new streaming method even after updating package.json

**Solution:**
1. Run `./scripts/nuclear-clean.sh` to wipe all caches
2. Hard refresh browser (Ctrl+Shift+R) with DevTools cache disabled
3. Check browser DevTools Sources tab to verify the correct file is loaded

### Issue: Method Not Found (404)

**Symptoms:** `POST /ai.pipestream.service.Name/MethodName` returns 404

**Check:**
1. Backend service has the method: `grpcurl -plaintext localhost:PORT list service.Name`
2. Web-proxy has proxy method in `connectRoutes.ts`
3. Vite proxy is configured for the path pattern in `vite.config.ts`

### Issue: Stream Connects but No Data

**Check:**
1. Backend implementation is actually emitting data
2. Test directly with grpcurl: `grpcurl -plaintext -d '{}' localhost:PORT service.Name/Method`
3. Check backend logs for errors

## Testing Streaming Calls

### Using grpcurl

```bash
# List available services
grpcurl -plaintext localhost:38101 list

# List methods for a service
grpcurl -plaintext localhost:38101 list ai.pipestream.platform.registration.PlatformRegistration

# Test streaming call (Ctrl+C to stop)
grpcurl -plaintext -d '{}' localhost:38101 \
  ai.pipestream.platform.registration.PlatformRegistration/WatchModules
```

### In Browser DevTools

1. Open Console tab
2. Watch for log messages from your store/composable
3. Check Network tab for the POST request
4. Verify the request stays open (pending) while streaming

## Best Practices

### Backend (Java)

1. **Send initial state immediately** - Improves UX, client sees data right away
2. **Handle errors gracefully** - Use `onFailure().recoverWithItem()`
3. **Log lifecycle events** - Start, completion, cancellation
4. **Use appropriate polling interval** - Balance freshness vs load (2-5 seconds typical)
5. **Implement proper cancellation** - Respect client disconnections

### Frontend (TypeScript)

1. **Always use binary format** - `useBinaryFormat: true` for better performance
2. **Implement reconnection logic** - Streams can disconnect, retry with exponential backoff
3. **Deduplicate updates** - Only update state when data actually changes
4. **Clean up on unmount** - Use `AbortController` to cancel streams
5. **Handle cancellation gracefully** - Don't log errors for `Code.Canceled`
6. **Set timeout to undefined** - Streaming connections should not timeout

### Web-Proxy

1. **Proxy all new methods** - Don't forget to add proxy in `connectRoutes.ts`
2. **Use same transport config** - Match timeout settings with backend
3. **Update grpc-stubs version** - Keep in sync with frontend and backend

## File Locations

- **Proto definitions**: `platform-libraries/grpc/grpc-stubs/src/main/proto/`
- **Regeneration script**: `platform-libraries/scripts/regenerate-stubs.sh`
- **Java backend**: `platform-registration-service/src/main/java/ai/pipestream/registration/`
- **Web-proxy routes**: `platform-frontend/apps/platform-shell/src/routes/connectRoutes.ts`
- **Frontend stores**: `platform-frontend/apps/platform-shell/ui/src/stores/`

## Version Management

### Auto-incrementing Alpha Versions

The regeneration script automatically publishes a new alpha version with timestamp:

```bash
# Format: 0.1.6-alpha.YYYYMMDDHHMMSS
# Example: 0.1.6-alpha.20251110123501
```

### Updating Frontend Dependencies

After regenerating stubs, update both:
1. `apps/platform-shell/package.json` (web-proxy backend)
2. `apps/platform-shell/ui/package.json` (Vue frontend)

Then run:
```bash
pnpm install
./scripts/nuclear-clean.sh  # To clear Vite caches
```

## Example: Complete WatchModules Implementation

See the `WatchModules` RPC in this codebase as a reference implementation:

- **Proto**: `grpc/grpc-stubs/src/main/proto/registration/platform_registration.proto:53`
- **Backend Handler**: `platform-registration-service/.../ServiceDiscoveryHandler.java:484`
- **Backend Service**: `platform-registration-service/.../PlatformRegistrationService.java:117`
- **Web-Proxy**: `platform-frontend/apps/platform-shell/src/routes/connectRoutes.ts:307`
- **Frontend Store**: `platform-frontend/apps/platform-shell/ui/src/stores/serviceRegistry.ts:79`

## Troubleshooting Checklist

When adding a new streaming call, verify:

- [ ] Proto file has `stream` keyword in return type
- [ ] Ran `./scripts/regenerate-stubs.sh` successfully
- [ ] Backend Java method returns `Multi<ResponseType>`
- [ ] Backend implementation sends initial data immediately
- [ ] Backend handler added to gRPC service class
- [ ] Backend service rebuilt: `./gradlew clean build`
- [ ] Web-proxy grpc-stubs version updated
- [ ] Web-proxy proxy method added with `async *` and `yield`
- [ ] Frontend grpc-stubs version updated
- [ ] Frontend ran `./scripts/nuclear-clean.sh`
- [ ] Frontend client uses `for await` loop
- [ ] Frontend handles `Code.Canceled` separately
- [ ] All three package.json files have same grpc-stubs version
- [ ] Browser DevTools shows correct number of methods
- [ ] Backend responds to grpcurl test
- [ ] Console shows stream connection logs

## Support

For issues with streaming:
1. Check backend logs for connection lifecycle events
2. Use grpcurl to test backend directly (bypass web-proxy)
3. Check browser Network tab for stuck/failed requests
4. Verify all three tiers use same grpc-stubs version
