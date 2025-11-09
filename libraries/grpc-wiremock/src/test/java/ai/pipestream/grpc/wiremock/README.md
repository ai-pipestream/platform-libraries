# Mock Service Factory Usage Guide

This guide explains how to use the `MockServiceFactory` to test services that depend on the Platform Registration Service.

## Overview

The `MockServiceFactory` provides a way to replace the real Platform Registration Service with a mock implementation during tests. This allows you to:

- Test services without requiring the real platform registration service to be running
- Control the behavior of the platform registration service in tests
- Test streaming registration flows without external dependencies

## Basic Usage

### 1. Start Mock Services

```java
@BeforeEach
void setUp() {
    MockServiceFactory.startMockPlatformRegistrationService();
}

@AfterEach
void tearDown() {
    MockServiceFactory.stopMockPlatformRegistrationService();
}
```

### 2. Use Mock Services

```java
@Test
void testServiceRegistration() throws InterruptedException {
    // Get the mock stub
    PlatformRegistrationGrpc.PlatformRegistrationStub stub = 
        MockServiceFactory.getPlatformRegistrationStub();
    
    // Use the stub as you would with the real service
    ServiceRegistrationRequest request = ServiceRegistrationRequest.newBuilder()
        .setServiceName("test-service")
        .setHost("localhost")
        .setPort(8080)
        .setVersion("1.0.0")
        .build();
    
    // The mock will return streaming responses
    stub.registerService(request, new StreamObserver<RegistrationEvent>() {
        @Override
        public void onNext(RegistrationEvent event) {
            System.out.println("Received: " + event.getMessage());
        }
        
        @Override
        public void onError(Throwable t) {
            // Handle error
        }
        
        @Override
        public void onCompleted() {
            // Handle completion
        }
    });
}
```

## Quarkus Integration

### Using Test Profiles

```java
@QuarkusTest
@TestProfile(MockServiceTestProfile.class)
public class MyServiceTest {
    // Mock services are automatically started/stopped
}
```

### Using Test Resources

```java
@QuarkusTest
@QuarkusTestResource(MockServiceTestResource.class)
public class MyServiceTest {
    // Mock services are automatically started/stopped
}
```

## Configuration

The mock services can be configured through Quarkus configuration:

```properties
# Enable mock services
pipeline.test.mock-services.enabled=true
pipeline.test.mock-services.platform-registration.enabled=true

# Override platform registration service endpoint
quarkus.grpc.clients.platform-registration.host=localhost
quarkus.grpc.clients.platform-registration.port=8080
```

## Features

### Streaming Support

The mock service supports full streaming responses:

- **Service Registration**: 6-phase streaming process
- **Module Registration**: 10-phase streaming process
- **Unary Calls**: ListServices, ListModules, etc.

### Realistic Behavior

The mock service simulates realistic registration flows:

- Proper event sequencing
- Realistic timing (with small delays)
- Complete success flows
- Proper error handling

### Easy Integration

- Drop-in replacement for real service
- No code changes required in service implementations
- Automatic startup/shutdown in tests

## Examples

See the test files for complete examples:

- `EchoServiceWithMockRegistrationTest.java` - Basic usage
- `QuarkusEchoServiceWithMockTest.java` - Quarkus integration
- `DirectWireMockStreamingWorkingTest.java` - Streaming examples

## Troubleshooting

### Port Conflicts

If you get port binding errors, the mock service uses dynamic ports by default. Make sure to call `getGrpcPort()` to get the actual port used.

### Service Not Running

Make sure to call `startMockPlatformRegistrationService()` before using the mock services. Check `isMockServiceRunning()` to verify the service is available.

### Configuration Issues

Ensure that your Quarkus configuration points to the mock service port when using test profiles or test resources.