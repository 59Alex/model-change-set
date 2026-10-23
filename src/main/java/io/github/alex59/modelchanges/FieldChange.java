package io.github.alex59.modelchanges;

import java.util.Objects;

/**
 * A changed configured field. Values are retained by reference and may be null.
 *
 * @param name configured field name
 * @param before value before the change
 * @param after value after the change
 */
public record FieldChange(String name, Object before, Object after) {
    /** Validates the field name. */
    public FieldChange {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("Field name must not be blank");
    }
}
