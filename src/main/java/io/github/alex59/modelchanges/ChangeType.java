package io.github.alex59.modelchanges;

/** The direction of a change from the before snapshot to the after snapshot. */
public enum ChangeType {
    /** A key exists only in the after snapshot. */
    ADDED,
    /** A key exists only in the before snapshot. */
    REMOVED,
    /** A key exists in both snapshots and a configured field differs. */
    MODIFIED
}
