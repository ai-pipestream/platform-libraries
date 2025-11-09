package ai.pipestream.grpc.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import ai.pipestream.platform.registration.PlatformRegistrationGrpc;
import ai.pipestream.platform.registration.RegistrationEvent;
import ai.pipestream.platform.registration.EventType;
import ai.pipestream.platform.registration.ServiceRegistrationRequest;
import ai.pipestream.platform.registration.ModuleRegistrationRequest;
import ai.pipestream.platform.registration.ServiceListResponse;
import ai.pipestream.platform.registration.ModuleListResponse;
import ai.pipestream.platform.registration.ServiceDetails;
import ai.pipestream.platform.registration.ModuleDetails;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * A direct gRPC server implementation that uses WireMock for request/response matching
 * but handles streaming responses properly.
 * 
 * This bypasses the WireMock gRPC extension's limitations and gives us full control
 * over streaming behavior.
 */
public class DirectWireMockGrpcServer {

    private final WireMockServer wireMockServer;
    private final Server grpcServer;
    private final int port;

    /**
     * Construct a DirectWireMockGrpcServer.
     *
     * <p>This spins up an embedded WireMock server on the given port for request matching,
     * and a separate gRPC server on a dynamically assigned port for serving mocked
     * gRPC endpoints. Use getGrpcPort() to obtain the gRPC port after construction.
     *
     * <p>Note: This path bypasses the WireMock gRPC extension so that true streaming
     * responses can be emitted in tests.
     *
     * @param port The HTTP port for the internal WireMock server. Use 0 for dynamic.
     */
    public DirectWireMockGrpcServer(int port) {
        this.port = port;
        
        // Start WireMock server
        this.wireMockServer = new WireMockServer(port);
        wireMockServer.start();
        
        // Create gRPC server on a different port
        this.grpcServer = ServerBuilder.forPort(0) // Use dynamic port
            .addService(new PlatformRegistrationServiceImpl())
            .build();
    }

    /**
     * Starts the embedded gRPC server.
     *
     * <p>WireMock is already started in the constructor; this method starts the
     * gRPC server so that clients can connect using the port returned by getGrpcPort().
     *
     * @throws IOException if the gRPC server cannot be started
     */
    public void start() throws IOException {
        grpcServer.start();
    }

    /**
     * Stops both the gRPC server and the internal WireMock server.
     *
     * <p>Attempts a graceful shutdown first, then forces termination if the server
     * does not stop within a short timeout.
     *
     * @throws InterruptedException if interrupted while waiting for shutdown
     */
    public void stop() throws InterruptedException {
        grpcServer.shutdown();
        wireMockServer.stop();
        
        if (!grpcServer.awaitTermination(5, TimeUnit.SECONDS)) {
            grpcServer.shutdownNow();
        }
    }

    /**
     * Returns the dynamically assigned gRPC server port.
     *
     * @return the port clients should connect to
     */
    public int getGrpcPort() {
        return grpcServer.getPort();
    }

    /**
     * Returns the internal WireMock server instance used for request matching.
     *
     * <p>Exposed for advanced test configuration or inspection.
     *
     * @return the WireMock server instance
     */
    public WireMockServer getWireMockServer() {
        return wireMockServer;
    }

    /**
     * Implementation of the Platform Registration Service that uses WireMock
     * for request matching but handles streaming responses directly.
     * 
     * <p>This inner class provides a concrete gRPC service implementation that bypasses
     * WireMock's streaming limitations by directly implementing the service methods.
     */
    private class PlatformRegistrationServiceImpl extends PlatformRegistrationGrpc.PlatformRegistrationImplBase {

        @Override
        public void registerService(ServiceRegistrationRequest request, StreamObserver<RegistrationEvent> responseObserver) {
            // Check if WireMock has a stub for this request
            // For now, we'll simulate the streaming response directly
            
            try {
                // Simulate the 6-phase service registration process
                responseObserver.onNext(RegistrationEvent.newBuilder()
                    .setEventType(EventType.STARTED)
                    .setMessage("Starting service registration")
                    .build());
                
                Thread.sleep(100); // Simulate processing time
                
                responseObserver.onNext(RegistrationEvent.newBuilder()
                    .setEventType(EventType.VALIDATED)
                    .setMessage("Service registration request validated")
                    .build());
                
                Thread.sleep(100);
                
                responseObserver.onNext(RegistrationEvent.newBuilder()
                    .setEventType(EventType.CONSUL_REGISTERED)
                    .setMessage("Service registered with Consul")
                    .build());
                
                Thread.sleep(100);
                
                responseObserver.onNext(RegistrationEvent.newBuilder()
                    .setEventType(EventType.HEALTH_CHECK_CONFIGURED)
                    .setMessage("Health check configured")
                    .build());
                
                Thread.sleep(100);
                
                responseObserver.onNext(RegistrationEvent.newBuilder()
                    .setEventType(EventType.CONSUL_HEALTHY)
                    .setMessage("Service reported healthy by Consul")
                    .build());
                
                Thread.sleep(100);
                
                responseObserver.onNext(RegistrationEvent.newBuilder()
                    .setEventType(EventType.COMPLETED)
                    .setMessage("Service registration completed successfully")
                    .build());
                
                responseObserver.onCompleted();
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                responseObserver.onError(io.grpc.Status.INTERNAL.withDescription("Interrupted").asRuntimeException());
            }
        }

        @Override
        public void registerModule(ModuleRegistrationRequest request, StreamObserver<RegistrationEvent> responseObserver) {
            // Simulate the 10-phase module registration process
            try {
                EventType[] phases = {
                    EventType.STARTED, EventType.VALIDATED, EventType.CONSUL_REGISTERED,
                    EventType.HEALTH_CHECK_CONFIGURED, EventType.CONSUL_HEALTHY,
                    EventType.METADATA_RETRIEVED, EventType.SCHEMA_VALIDATED,
                    EventType.DATABASE_SAVED, EventType.APICURIO_REGISTERED, EventType.COMPLETED
                };

                String[] messages = {
                    "Starting module registration",
                    "Module registration request validated",
                    "Module registered with Consul",
                    "Health check configured",
                    "Module reported healthy by Consul",
                    "Module metadata retrieved",
                    "Schema validated or synthesized",
                    "Module registration saved to database",
                    "Schema registered in Apicurio",
                    "Module registration completed successfully"
                };

                for (int i = 0; i < phases.length; i++) {
                    responseObserver.onNext(RegistrationEvent.newBuilder()
                        .setEventType(phases[i])
                        .setMessage(messages[i])
                        .build());

                    Thread.sleep(50); // Simulate processing time
                }
                
                responseObserver.onCompleted();
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                responseObserver.onError(io.grpc.Status.INTERNAL.withDescription("Interrupted").asRuntimeException());
            }
        }

        @Override
        public void listServices(Empty request, StreamObserver<ServiceListResponse> responseObserver) {
            // Return a simple list of services
            ServiceListResponse response = ServiceListResponse.newBuilder()
                .addServices(ServiceDetails.newBuilder()
                    .setServiceName("repository-service")
                    .setServiceId("repo-1")
                    .setHost("localhost")
                    .setPort(8080)
                    .setVersion("1.0.0")
                    .setIsHealthy(true)
                    .setRegisteredAt(Timestamp.getDefaultInstance())
                    .setLastHealthCheck(Timestamp.getDefaultInstance())
                    .build())
                .addServices(ServiceDetails.newBuilder()
                    .setServiceName("account-manager")
                    .setServiceId("account-1")
                    .setHost("localhost")
                    .setPort(38105)
                    .setVersion("1.0.0")
                    .setIsHealthy(true)
                    .setRegisteredAt(Timestamp.getDefaultInstance())
                    .setLastHealthCheck(Timestamp.getDefaultInstance())
                    .build())
                .setAsOf(Timestamp.getDefaultInstance())
                .setTotalCount(2)
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void listModules(Empty request, StreamObserver<ModuleListResponse> responseObserver) {
            // Return a simple list of modules
            ModuleListResponse response = ModuleListResponse.newBuilder()
                .addModules(ModuleDetails.newBuilder()
                    .setModuleName("parser")
                    .setServiceId("parser-1")
                    .setHost("localhost")
                    .setPort(8081)
                    .setVersion("1.0.0")
                    .setInputFormat("text/plain")
                    .setOutputFormat("application/json")
                    .addDocumentTypes("text")
                    .setIsHealthy(true)
                    .setRegisteredAt(Timestamp.getDefaultInstance())
                    .setLastHealthCheck(Timestamp.getDefaultInstance())
                    .build())
                .addModules(ModuleDetails.newBuilder()
                    .setModuleName("chunker")
                    .setServiceId("chunker-1")
                    .setHost("localhost")
                    .setPort(8082)
                    .setVersion("1.0.0")
                    .setInputFormat("application/json")
                    .setOutputFormat("application/json")
                    .addDocumentTypes("text")
                    .setIsHealthy(true)
                    .setRegisteredAt(Timestamp.getDefaultInstance())
                    .setLastHealthCheck(Timestamp.getDefaultInstance())
                    .build())
                .setAsOf(Timestamp.getDefaultInstance())
                .setTotalCount(2)
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}