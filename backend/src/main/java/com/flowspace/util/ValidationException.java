package com.flowspace.util;

/**
 * Thrown when an entity violates domain validation rules.
 */
public class ValidationException extends RuntimeException {
    private final String fieldName;

    public ValidationException(String message) {
        super(message);
        this.fieldName = null;
    }

    public ValidationException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
