// java
package ai.pipestream.common.util;

import com.google.protobuf.Message;
import java.util.List;

/**
 * Interface for mapping fields between protobuf messages.
 * Declares the public API implemented by ProtoFieldMapperImpl.
 */
public interface ProtoFieldMapper {

    DescriptorRegistry getDescriptorRegistry();

    AnyHandler getAnyHandler();

    void map(Message source, Message.Builder targetBuilder, List<String> rules)
            throws ProtoFieldMapperImpl.MappingException;

    static ProtoFieldMapper withAutoLoad() {
        return ProtoFieldMapperImpl.withAutoLoad();
    }
}
