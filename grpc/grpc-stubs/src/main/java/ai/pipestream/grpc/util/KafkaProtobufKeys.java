package ai.pipestream.grpc.util;

import ai.pipestream.config.v1.GraphUpdateNotification;
import ai.pipestream.data.module.ModuleProcessRequest;
import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.data.v1.PipeStream;
import ai.pipestream.engine.v1.ProcessNodeRequest;
import ai.pipestream.ingestion.proto.IngestionRequest;
import ai.pipestream.linear.processor.v1.BatchLinearProcessRequest;
import ai.pipestream.platform.registration.ModuleRegistered;
import ai.pipestream.platform.registration.ModuleUnregistered;
import ai.pipestream.platform.registration.ServiceRegistered;
import ai.pipestream.platform.registration.ServiceUnregistered;
import ai.pipestream.repository.account.AccountEvent;
import ai.pipestream.repository.crawler.CrawlDirectoryRequest;
import ai.pipestream.repository.filesystem.DriveUpdateNotification;
import ai.pipestream.repository.filesystem.RepositoryEvent;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Type-safe utility for generating deterministic Kafka keys from Protobuf events.
 * <p>
 * This ensures that every event type uses the correct identity field for its Kafka partition key,
 * guaranteeing correct log compaction.
 */
public class KafkaProtobufKeys {

    private KafkaProtobufKeys() {
        // Utility class
    }

    /**
     * Generates a deterministic UUID key for DriveUpdateNotification based on Drive Name.
     */
    public static UUID uuid(DriveUpdateNotification event) {
        return uuidFrom(event.getDrive().getName());
    }

    /**
     * Generates a deterministic UUID key for AccountEvent based on Account ID.
     */
    public static UUID uuid(AccountEvent event) {
        return uuidFrom(event.getAccountId());
    }

    /**
     * Generates a deterministic UUID key for ServiceRegistered based on Service ID.
     */
    public static UUID uuid(ServiceRegistered event) {
        return uuidFrom(event.getServiceId());
    }

    /**
     * Generates a deterministic UUID key for ServiceUnregistered based on Service ID.
     */
    public static UUID uuid(ServiceUnregistered event) {
        return uuidFrom(event.getServiceId());
    }

    /**
     * Generates a deterministic UUID key for ModuleRegistered based on Service ID.
     */
    public static UUID uuid(ModuleRegistered event) {
        return uuidFrom(event.getServiceId());
    }

    /**
     * Generates a deterministic UUID key for ModuleUnregistered based on Service ID.
     */
    public static UUID uuid(ModuleUnregistered event) {
        return uuidFrom(event.getServiceId());
    }

    /**
     * Generates a deterministic UUID key for RepositoryEvent based on Document ID.
     */
    public static UUID uuid(RepositoryEvent event) {
        return uuidFrom(event.getDocumentId());
    }

    /**
     * Generates a deterministic UUID key for PipeDoc based on Document ID.
     */
    public static UUID uuid(PipeDoc doc) {
        return uuidFrom(doc.getDocId());
    }

    /**
     * Generates a deterministic UUID key for PipeStream based on Stream ID, Current Node, and Hop Count.
     * <p>
     * <b>Composite Key:</b> {@code hash(stream_id + "/" + current_node_id + "/" + hop_count)}
     * <p>
     * This creates a unique identity for the stream <i>at this specific point in the network topology</i>.
     * By including the node ID and hop count, we ensure that the Kafka log retains a "snapshot" of the
     * stream's state at every step of execution. This is critical for:
     * <ul>
     *     <li><b>Auditing:</b> Tracing exactly where a document went and how it looked at each hop.</li>
     *     <li><b>Reprocessing:</b> Replaying a stream from a specific node/hop without restarting from scratch.</li>
     *     <li><b>Debugging:</b> Isolating state at a point of failure.</li>
     * </ul>
     */
    public static UUID uuid(PipeStream stream) {
        // Fallback for initial streams that might not have node/hop set yet
        if (stream.getCurrentNodeId().isEmpty()) {
            return uuidFrom(stream.getStreamId());
        }
        return uuidFrom(stream.getStreamId() + "/" + stream.getCurrentNodeId() + "/" + stream.getHopCount());
    }

    /**
     * Generates a deterministic UUID key for GraphUpdateNotification based on Cluster ID.
     * Ensures all updates for a specific cluster topology are ordered.
     */
    public static UUID uuid(GraphUpdateNotification event) {
        return uuidFrom(event.getClusterId());
    }

    /**
     * Generates a deterministic UUID key for ModuleProcessRequest based on Stream ID, Step Name, and Hop Number.
     * <p>
     * <b>Composite Key:</b> {@code hash(stream_id + "/" + step_name + "/" + hop_number)}
     * <p>
     * This supports the "snapshot" philosophy. Each request to a module is keyed uniquely to that specific
     * execution attempt within the pipeline. This granularity allows for:
     * <ul>
     *     <li>Precise retries of specific steps.</li>
     *     <li>Parallel processing of different steps for the same stream (if they branch).</li>
     *     <li>Clear audit trails of which version of a step processed a document.</li>
     * </ul>
     */
    public static UUID uuid(ModuleProcessRequest request) {
        String streamId = request.getMetadata().getStreamId();
        String stepName = request.getMetadata().getPipeStepName();
        long hop = request.getMetadata().getCurrentHopNumber();
        
        // Fallback if metadata is missing (e.g. direct test calls)
        if (streamId.isEmpty()) {
            return uuidFrom(request.getDocument().getDocId());
        }
        return uuidFrom(streamId + "/" + stepName + "/" + hop);
    }

    /**
     * Generates a deterministic UUID key for IngestionRequest based on Request ID.
     */
    public static UUID uuid(IngestionRequest request) {
        return uuidFrom(request.getRequestId());
    }

    /**
     * Generates a deterministic UUID key for ProcessNodeRequest based on Stream ID.
     * This ensures ordering for the stream's traversal.
     */
    public static UUID uuid(ProcessNodeRequest request) {
        return uuidFrom(request.getStream().getStreamId());
    }

    /**
     * Generates a deterministic UUID key for CrawlDirectoryRequest based on Connector ID.
     * Ensures crawls for the same connector are serial or partitioned together.
     */
    public static UUID uuid(CrawlDirectoryRequest request) {
        return uuidFrom(request.getConnectorId());
    }

    /**
     * Generates a deterministic UUID key for BatchLinearProcessRequest based on Pipeline ID and Document ID.
     * <p>
     * <b>Composite Key:</b> {@code hash(pipeline_id + "/" + document_id)}
     * <p>
     * Groups processing requests by the specific pipeline/document combination.
     */
    public static UUID uuid(BatchLinearProcessRequest request) {
        return uuidFrom(request.getPipelineId() + "/" + request.getDocumentId());
    }

    private static UUID uuidFrom(String id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null for Kafka key generation");
        }
        return UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8));
    }
}

