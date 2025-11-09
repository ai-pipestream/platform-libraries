# TODO: Fix Quarkus Test Classloader Issues

## Issue
After upgrading to grpc 1.76.0 and protobuf 4.33.0, two tests fail with LinkageError:

1. `QuarkusEchoServiceWithMockTest.testMockServicesAreAvailable()`
2. `PlatformRegistrationClientMockIntegrationTest`

## Root Cause
Quarkus test classloader is loading different Class objects for:
- `io.grpc.Channel`
- `io.smallrye.mutiny.Uni`

This causes loader constraint violations when the app classloader and test classloader 
have different instances of the same class.

## Error Messages
```
loader constraint violation: when resolving method 
'ai.pipestream.platform.registration.MutinyPlatformRegistrationGrpc$MutinyPlatformRegistrationStub 
ai.pipestream.platform.registration.MutinyPlatformRegistrationGrpc.newMutinyStub(io.grpc.Channel)'
the class loader QuarkusClassLoader and the class loader 'app' have different Class objects 
for the type io/grpc/Channel
```

## Impact
- **Build**: ✅ Works fine - JAR is built correctly with descriptor
- **Runtime**: ✅ Should work fine - this is a test-only issue
- **Tests**: ❌ 2 integration tests fail

## Next Steps
1. Investigate Quarkus test configuration in `grpc-wiremock/build.gradle`
2. Check if we need to adjust parent-first classloading for gRPC classes
3. Consider upgrading Quarkus version if this is a known issue
4. May need to adjust `@QuarkusTest` configuration

## References
- Build works: descriptor at `META-INF/grpc/services.dsc`
- protobuf: 4.33.0
- grpc: 1.76.0
- Quarkus: 3.28.x (from BOM)

## Created
2025-10-25
