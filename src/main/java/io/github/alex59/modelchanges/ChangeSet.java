package io.github.alex59.modelchanges;

import java.util.List;

/**
 * An immutable list of model changes. Results produced by {@link ModelDiffer}
 * are ordered by its key comparator; unchanged models are omitted. Returned
 * lists and their iterators are unmodifiable. Traversing a list backwards does
 * not invert the direction of the changes.
 *
 * @param changes model changes
 * @param <T> model type
 * @param <K> key type
 */
public record ChangeSet<T, K>(List<Change<T, K>> changes) {
    /**
     * Copies the result list; underlying model values are not deep-copied.
     * @param changes model changes to retain in their existing order
     */
    public ChangeSet { changes = List.copyOf(changes); }

    /** Tests whether the snapshots have no configured differences.
     * @return true if no configured differences were found */
    public boolean isEmpty() { return changes.isEmpty(); }

    /** Selects additions.
     * @return added models in key order */
    public List<Change<T, K>> added() { return ofType(ChangeType.ADDED); }

    /** Selects removals.
     * @return removed models in key order */
    public List<Change<T, K>> removed() { return ofType(ChangeType.REMOVED); }

    /** Selects modifications.
     * @return modified models in key order */
    public List<Change<T, K>> modified() { return ofType(ChangeType.MODIFIED); }

    private List<Change<T, K>> ofType(ChangeType type) {
        return changes.stream().filter(change -> change.type() == type).toList();
    }
}
