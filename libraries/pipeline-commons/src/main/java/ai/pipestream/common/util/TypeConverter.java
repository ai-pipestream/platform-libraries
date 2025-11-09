package ai.pipestream.common.util;

import com.google.protobuf.*;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for converting between different Protocol Buffer types.
 * Handles conversions between messages, primitives, Struct, Value, and common Java types.
 */
public class TypeConverter {

    /**
     * Converts a Java object to a protobuf Value.
     * Supports null, String, Number, Boolean, Struct, List, and Message types.
     *
     * @param value The value to convert
     * @return A protobuf Value representation
     * @throws IllegalArgumentException if the type cannot be converted
     */
    public Value toValue(Object value) {
        if (value == null) {
            return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
        }
        if (value instanceof String) {
            return Value.newBuilder().setStringValue((String) value).build();
        }
        if (value instanceof Double) {
            return Value.newBuilder().setNumberValue((Double) value).build();
        }
        if (value instanceof Float) {
            return Value.newBuilder().setNumberValue(((Float) value).doubleValue()).build();
        }
        if (value instanceof Number) {
            return Value.newBuilder().setNumberValue(((Number) value).doubleValue()).build();
        }
        if (value instanceof Boolean) {
            return Value.newBuilder().setBoolValue((Boolean) value).build();
        }
        if (value instanceof Struct) {
            return Value.newBuilder().setStructValue((Struct) value).build();
        }
        if (value instanceof List) {
            ListValue.Builder listBuilder = ListValue.newBuilder();
            for (Object item : (List<?>) value) {
                listBuilder.addValues(toValue(item));
            }
            return Value.newBuilder().setListValue(listBuilder).build();
        }
        if (value instanceof Message) {
            // Convert message to Struct representation
            return Value.newBuilder().setStructValue(messageToStruct((Message) value)).build();
        }
        throw new IllegalArgumentException("Cannot convert type to Value: " + value.getClass().getName());
    }

    /**
     * Converts a protobuf Value to a Java object.
     *
     * @param value The Value to convert
     * @return The unwrapped Java object (String, Double, Boolean, Struct, List, or null)
     */
    public Object fromValue(Value value) {
        if (value == null || value.getKindCase() == Value.KindCase.NULL_VALUE) {
            return null;
        }
        switch (value.getKindCase()) {
            case NUMBER_VALUE:
                return value.getNumberValue();
            case STRING_VALUE:
                return value.getStringValue();
            case BOOL_VALUE:
                return value.getBoolValue();
            case STRUCT_VALUE:
                return value.getStructValue();
            case LIST_VALUE:
                return value.getListValue().getValuesList().stream()
                        .map(this::fromValue)
                        .collect(Collectors.toList());
            default:
                return null;
        }
    }

    /**
     * Converts a protobuf Message to a Struct.
     * Recursively converts all fields to Value representations.
     *
     * @param message The message to convert
     * @return A Struct representation of the message
     */
    public Struct messageToStruct(Message message) {
        Struct.Builder structBuilder = Struct.newBuilder();
        Descriptor descriptor = message.getDescriptorForType();

        for (FieldDescriptor field : descriptor.getFields()) {
            if (!message.hasField(field) && !field.isRepeated()) {
                continue;
            }

            String fieldName = field.getName();
            Object fieldValue = message.getField(field);

            if (field.isRepeated()) {
                List<?> values = (List<?>) fieldValue;
                if (!values.isEmpty()) {
                    ListValue.Builder listBuilder = ListValue.newBuilder();
                    for (Object item : values) {
                        listBuilder.addValues(fieldToValue(field, item));
                    }
                    structBuilder.putFields(fieldName, Value.newBuilder().setListValue(listBuilder).build());
                }
            } else {
                structBuilder.putFields(fieldName, fieldToValue(field, fieldValue));
            }
        }

        return structBuilder.build();
    }

    /**
     * Converts a Struct to a DynamicMessage of the specified type.
     *
     * @param struct The Struct to convert
     * @param descriptor The descriptor of the target message type
     * @return A DynamicMessage built from the Struct
     * @throws IllegalArgumentException if conversion fails
     */
    public DynamicMessage structToMessage(Struct struct, Descriptor descriptor) {
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(descriptor);

        for (Map.Entry<String, Value> entry : struct.getFieldsMap().entrySet()) {
            FieldDescriptor field = descriptor.findFieldByName(entry.getKey());
            if (field == null) {
                // Skip unknown fields
                continue;
            }

            Object convertedValue = valueToField(entry.getValue(), field);
            if (convertedValue != null) {
                if (field.isRepeated() && convertedValue instanceof List) {
                    for (Object item : (List<?>) convertedValue) {
                        builder.addRepeatedField(field, item);
                    }
                } else {
                    builder.setField(field, convertedValue);
                }
            }
        }

        return builder.build();
    }

    /**
     * Converts a field value to a protobuf Value based on the field descriptor.
     *
     * @param field The field descriptor
     * @param value The field value
     * @return A Value representation
     */
    private Value fieldToValue(FieldDescriptor field, Object value) {
        if (value == null) {
            return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
        }

        switch (field.getJavaType()) {
            case INT:
            case LONG:
            case FLOAT:
            case DOUBLE:
                return Value.newBuilder().setNumberValue(((Number) value).doubleValue()).build();
            case BOOLEAN:
                return Value.newBuilder().setBoolValue((Boolean) value).build();
            case STRING:
                return Value.newBuilder().setStringValue((String) value).build();
            case ENUM:
                EnumValueDescriptor enumValue = (EnumValueDescriptor) value;
                return Value.newBuilder().setStringValue(enumValue.getName()).build();
            case MESSAGE:
                if (value instanceof Struct) {
                    return Value.newBuilder().setStructValue((Struct) value).build();
                }
                return Value.newBuilder().setStructValue(messageToStruct((Message) value)).build();
            case BYTE_STRING:
                // Encode as base64 string
                ByteString bytes = (ByteString) value;
                return Value.newBuilder().setStringValue(bytes.toStringUtf8()).build();
            default:
                return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
        }
    }

    /**
     * Converts a protobuf Value to a field value based on the field descriptor.
     *
     * @param value The Value to convert
     * @param field The target field descriptor
     * @return The converted value appropriate for the field type
     */
    private Object valueToField(Value value, FieldDescriptor field) {
        if (value.getKindCase() == Value.KindCase.NULL_VALUE) {
            return null;
        }

        switch (field.getJavaType()) {
            case INT:
                return (int) value.getNumberValue();
            case LONG:
                return (long) value.getNumberValue();
            case FLOAT:
                return (float) value.getNumberValue();
            case DOUBLE:
                return value.getNumberValue();
            case BOOLEAN:
                return value.getBoolValue();
            case STRING:
                return value.getStringValue();
            case ENUM:
                EnumDescriptor enumDescriptor = field.getEnumType();
                return enumDescriptor.findValueByName(value.getStringValue());
            case MESSAGE:
                if (field.getMessageType().getFullName().equals(Struct.getDescriptor().getFullName())) {
                    return value.getStructValue();
                }
                if (value.getKindCase() == Value.KindCase.STRUCT_VALUE) {
                    return structToMessage(value.getStructValue(), field.getMessageType());
                }
                return null;
            case BYTE_STRING:
                return ByteString.copyFromUtf8(value.getStringValue());
            default:
                return null;
        }
    }

    /**
     * Attempts to convert a value to match the expected type of a field.
     * Provides intelligent type coercion where possible.
     *
     * @param value The value to convert
     * @param field The target field descriptor
     * @return The converted value, or the original if no conversion is needed
     * @throws IllegalArgumentException if conversion is not possible
     */
    public Object convertToFieldType(Object value, FieldDescriptor field) {
        if (value == null) {
            return null;
        }

        // If value is already compatible, return as-is
        if (isCompatibleType(value, field)) {
            return value;
        }

        // Attempt type conversions
        switch (field.getJavaType()) {
            case INT:
                if (value instanceof Number) return ((Number) value).intValue();
                if (value instanceof String) return Integer.parseInt((String) value);
                break;
            case LONG:
                if (value instanceof Number) return ((Number) value).longValue();
                if (value instanceof String) return Long.parseLong((String) value);
                break;
            case FLOAT:
                if (value instanceof Number) return ((Number) value).floatValue();
                if (value instanceof String) return Float.parseFloat((String) value);
                break;
            case DOUBLE:
                if (value instanceof Number) return ((Number) value).doubleValue();
                if (value instanceof String) return Double.parseDouble((String) value);
                break;
            case BOOLEAN:
                if (value instanceof String) return Boolean.parseBoolean((String) value);
                break;
            case STRING:
                return value.toString();
            case MESSAGE:
                if (value instanceof Struct &&
                    !field.getMessageType().getFullName().equals(Struct.getDescriptor().getFullName())) {
                    return structToMessage((Struct) value, field.getMessageType());
                }
                break;
        }

        throw new IllegalArgumentException(
            "Cannot convert value of type " + value.getClass().getName() +
            " to field type " + field.getJavaType());
    }

    /**
     * Checks if a value is compatible with a field type.
     *
     * @param value The value to check
     * @param field The field descriptor
     * @return true if the value can be assigned to the field without conversion
     */
    private boolean isCompatibleType(Object value, FieldDescriptor field) {
        switch (field.getJavaType()) {
            case INT:
                return value instanceof Integer;
            case LONG:
                return value instanceof Long;
            case FLOAT:
                return value instanceof Float;
            case DOUBLE:
                return value instanceof Double;
            case BOOLEAN:
                return value instanceof Boolean;
            case STRING:
                return value instanceof String;
            case BYTE_STRING:
                return value instanceof ByteString;
            case ENUM:
                return value instanceof EnumValueDescriptor;
            case MESSAGE:
                return value instanceof Message;
            default:
                return false;
        }
    }
}
