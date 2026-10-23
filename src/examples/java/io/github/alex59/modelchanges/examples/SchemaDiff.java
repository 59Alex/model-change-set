package io.github.alex59.modelchanges.examples;

import io.github.alex59.modelchanges.Field;
import io.github.alex59.modelchanges.ModelDiffer;

import java.util.Comparator;
import java.util.List;

/** A self-contained schema snapshot comparison. Run with ./gradlew runExample. */
public final class SchemaDiff {
    /** A logical column, identified across snapshots by its qualified name. */
    public record Column(String qualifiedName, String type, boolean nullable) { }

    /** Runs the example using the standard instance-main entry point. */
    void main() {
        var differ = ModelDiffer.<Column, String>of(Column::qualifiedName, Comparator.naturalOrder(), List.of(
                Field.of("type", Column::type),
                Field.of("nullable", Column::nullable)));

        var before = List.of(new Column("orders.id", "bigint", false),
                new Column("orders.note", "text", true),
                new Column("orders.legacy", "text", true));
        var after = List.of(new Column("orders.id", "bigint", false),
                new Column("orders.note", "text", false),
                new Column("orders.created_at", "timestamp", false));

        differ.compare(before, after).changes().forEach(change -> {
            System.out.println(change.type() + " " + change.key());
            change.fields().forEach(field -> System.out.println("  " + field.name() + ": "
                    + field.before() + " -> " + field.after()));
        });
    }
}
