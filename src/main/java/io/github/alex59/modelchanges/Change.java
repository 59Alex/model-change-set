package io.github.alex59.modelchanges;

import java.util.List;
import java.util.Objects;

/**
 * One model change. Container collections are immutable; model and field values
 * are retained by reference. Added and removed models have no per-field changes.
 *
 * @param key stable identity, never null
 * @param type direction of this change
 * @param before old model; null only for ADDED
 * @param after new model; null only for REMOVED
 * @param fields changed fields, in configured order; non-empty only for MODIFIED
 * @param <T> model type
 * @param <K> key type
 */
public record Change<T, K>(K key, ChangeType type, T before, T after, List<FieldChange> fields) {
    /** Validates change shape and copies the field list. */
    public Change {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        fields = List.copyOf(fields);
        boolean valid = switch (type) {
            case ADDED -> before == null && after != null && fields.isEmpty();
            case REMOVED -> before != null && after == null && fields.isEmpty();
            case MODIFIED -> before != null && after != null && !fields.isEmpty();
        };
        if (!valid) throw new IllegalArgumentException("Invalid change shape for " + type);
    }
}
