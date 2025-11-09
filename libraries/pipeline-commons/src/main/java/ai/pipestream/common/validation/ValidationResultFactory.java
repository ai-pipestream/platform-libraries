package ai.pipestream.common.validation;


import ai.pipestream.api.validation.ValidationResult;

import java.util.List;

/**
 * Factory for creating ValidationResult instances.
 */
public final class ValidationResultFactory {

    private ValidationResultFactory() {
        // Prevent instantiation
    }

    /**
     * Creates a valid result with no errors or warnings.
     * 
     * @return a valid ValidationResult with no errors or warnings
     */
    public static ValidationResult success() {
        return new DefaultValidationResult(true, List.of(), List.of());
    }

    /**
     * Creates a valid result with warnings.
     * 
     * @param warnings list of warning messages
     * @return a valid ValidationResult with the specified warnings
     */
    public static ValidationResult successWithWarnings(List<String> warnings) {
        return new DefaultValidationResult(true, List.of(), warnings);
    }

    /**
     * Creates an invalid result with the given errors.
     * 
     * @param errors list of error messages
     * @return an invalid ValidationResult with the specified errors
     */
    public static ValidationResult failure(List<String> errors) {
        return new DefaultValidationResult(false, errors, List.of());
    }

    /**
     * Creates an invalid result with a single error.
     * 
     * @param error the error message
     * @return an invalid ValidationResult with the specified error
     */
    public static ValidationResult failure(String error) {
        return new DefaultValidationResult(false, List.of(error), List.of());
    }

    /**
     * Creates an invalid result with errors and warnings.
     * 
     * @param errors list of error messages
     * @param warnings list of warning messages
     * @return an invalid ValidationResult with the specified errors and warnings
     */
    public static ValidationResult failure(List<String> errors, List<String> warnings) {
        return new DefaultValidationResult(false, errors, warnings);
    }
}
