package io.github.alex59.modelchanges;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.SequencedCollection;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ModelDifferTest {
    record Column(String key, String type, String comment, long updatedAt) { }

    private final ModelDiffer<Column, String> differ = ModelDiffer.of(
            Column::key, Comparator.naturalOrder(), List.of(
                    Field.of("type", Column::type), Field.of("comment", Column::comment)));

    private Column column(String key, String type) { return new Column(key, type, null, 0); }

    @Test void detectsAddedRemovedModifiedAndOmitsUnchanged() {
        var before = List.of(column("removed", "text"), column("modified", "int"), column("same", "text"));
        var after = List.of(column("same", "text"), column("added", "bool"), column("modified", "bigint"));
        var result = differ.compare(before, after);

        assertEquals(List.of("added", "modified", "removed"), result.changes().stream().map(Change::key).toList());
        assertEquals(List.of(ChangeType.ADDED, ChangeType.MODIFIED, ChangeType.REMOVED),
                result.changes().stream().map(Change::type).toList());
        assertNull(result.added().getFirst().before());
        assertEquals(column("added", "bool"), result.added().getFirst().after());
        assertEquals(column("removed", "text"), result.removed().getFirst().before());
        assertNull(result.removed().getFirst().after());
        assertEquals(List.of(new FieldChange("type", "int", "bigint")), result.modified().getFirst().fields());
    }

    @Test void arbitraryInputOrderingNeverChangesTheReport() {
        var before = new ArrayList<>(List.of(column("d", "int"), column("b", "int"), column("a", "int")));
        var after = new ArrayList<>(List.of(column("c", "text"), column("d", "text"), column("b", "int")));
        var expected = differ.compare(before, after);
        var random = new Random(34);
        for (int i = 0; i < 100; i++) {
            Collections.shuffle(before, random);
            Collections.shuffle(after, random);
            assertEquals(expected, differ.compare(before, after));
        }
    }

    @Test void sequencedViewsRetainKeyOrderAndCannotMutateTheReport() {
        var result = differ.compare(List.of(column("b", "int")),
                List.of(column("a", "text"), column("c", "text")));
        SequencedCollection<Change<Column, String>> ordered = result.changes();
        assertEquals("a", ordered.getFirst().key());
        assertEquals("c", ordered.getLast().key());

        var reversed = ordered.reversed();
        assertEquals(List.of("c", "b", "a"), reversed.stream().map(Change::key).toList());
        assertEquals(ChangeType.ADDED, reversed.getFirst().type());
        assertEquals(ChangeType.REMOVED, result.removed().getFirst().type());
        assertThrows(UnsupportedOperationException.class, reversed::removeFirst);
        assertThrows(UnsupportedOperationException.class, () -> reversed.addLast(ordered.getFirst()));
        assertEquals(List.of("a", "b", "c"), result.changes().stream().map(Change::key).toList());
    }

    @Test void sequencedEndpointsOnEmptyReportsDoNotInventAChange() {
        var empty = differ.compare(List.of(), List.of()).changes();
        assertThrows(NoSuchElementException.class, empty::getFirst);
        assertThrows(NoSuchElementException.class, empty::getLast);
        assertTrue(empty.reversed().isEmpty());
    }

    @Test void emptyAndUnchangedSnapshotsProduceEmptyResults() {
        assertTrue(differ.compare(List.of(), List.of()).isEmpty());
        assertTrue(differ.compare(List.of(column("id", "int")), List.of(column("id", "int"))).isEmpty());
    }

    @Test void changingIdentityIsADeletionAndAddition() {
        var result = differ.compare(List.of(column("old", "int")), List.of(column("new", "int")));
        assertEquals(1, result.added().size());
        assertEquals(1, result.removed().size());
        assertTrue(result.modified().isEmpty());
    }

    @Test void unconfiguredTechnicalFieldsDoNotProduceChanges() {
        var before = new Column("id", "int", null, 1);
        var after = new Column("id", "int", null, 2);
        assertTrue(differ.compare(List.of(before), List.of(after)).isEmpty());
    }

    @Test void nullFieldsRemainDifferentFromEmptyStringsAndMissingModels() {
        var result = differ.compare(List.of(column("id", null)), List.of(column("id", "")));
        assertEquals(List.of(new FieldChange("type", null, "")), result.modified().getFirst().fields());
        var addedNull = differ.compare(List.of(), List.of(column("id", null)));
        assertEquals(ChangeType.ADDED, addedNull.changes().getFirst().type());
        assertTrue(addedNull.changes().getFirst().fields().isEmpty());
    }

    @Test void supportsNullAwareDomainEquivalenceAndRetainsOriginalChangedValues() {
        record Price(String key, BigDecimal amount) { }
        var priceDiffer = ModelDiffer.<Price, String>of(Price::key, Comparator.naturalOrder(), List.of(
                Field.comparing("amount", Price::amount, (a, b) -> a == null ? b == null
                        : b != null && a.compareTo(b) == 0)));
        assertTrue(priceDiffer.compare(List.of(new Price("x", new BigDecimal("1.0"))),
                List.of(new Price("x", new BigDecimal("1.00")))).isEmpty());
        var changed = priceDiffer.compare(List.of(new Price("x", null)),
                List.of(new Price("x", new BigDecimal("1.00"))));
        assertEquals(new BigDecimal("1.00"), changed.modified().getFirst().fields().getFirst().after());
    }

    @Test void defaultComparisonSupportsPrimitiveArrayValues() {
        record Value(int key, byte[] bytes) { }
        var arrayDiffer = ModelDiffer.<Value, Integer>of(Value::key, Comparator.naturalOrder(),
                List.of(Field.of("bytes", Value::bytes)));
        assertTrue(arrayDiffer.compare(List.of(new Value(1, new byte[]{1, 2})),
                List.of(new Value(1, new byte[]{1, 2}))).isEmpty());
        assertEquals(1, arrayDiffer.compare(List.of(new Value(1, new byte[]{1, 2})),
                List.of(new Value(1, new byte[]{2, 1}))).modified().size());
    }

    @Test void duplicateKeysAreRejectedOnEitherSideEvenForIdenticalObjects() {
        var duplicate = List.of(column("id", "int"), column("id", "int"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> differ.compare(duplicate, List.of()))
                .getMessage().contains("before"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> differ.compare(List.of(), duplicate))
                .getMessage().contains("after"));
    }

    @Test void comparatorCollisionsAreRejectedWithinAndAcrossSnapshots() {
        var insensitive = ModelDiffer.<Column, String>of(Column::key, String.CASE_INSENSITIVE_ORDER,
                List.of(Field.of("type", Column::type)));
        assertThrows(IllegalArgumentException.class, () -> insensitive.compare(
                List.of(column("a", "int"), column("A", "int")), List.of()));
        assertThrows(IllegalArgumentException.class, () -> insensitive.compare(
                List.of(column("a", "int")), List.of(column("A", "int"))));
    }

    @Test void nullInputsModelsAndKeysAreRejected() {
        assertThrows(NullPointerException.class, () -> differ.compare(null, List.of()));
        assertThrows(NullPointerException.class, () -> differ.compare(List.of(), null));
        assertThrows(NullPointerException.class, () -> differ.compare(Arrays.asList((Column) null), List.of()));
        assertThrows(NullPointerException.class, () -> differ.compare(List.of(), List.of(column(null, "int"))));
    }

    @Test void duplicateFieldNamesAreRejectedAndConfiguredOrderIsPreserved() {
        assertThrows(IllegalArgumentException.class, () -> ModelDiffer.of(Column::key,
                Comparator.naturalOrder(), List.of(Field.of("x", Column::type), Field.of("x", Column::comment))));
        var change = differ.compare(List.of(new Column("id", "int", "old", 0)),
                List.of(new Column("id", "text", "new", 0))).modified().getFirst();
        assertEquals(List.of("type", "comment"), change.fields().stream().map(FieldChange::name).toList());
    }

    @Test void membershipOnlyDifferIgnoresFields() {
        var membership = ModelDiffer.<Column, String>of(Column::key, Comparator.naturalOrder(), List.of());
        assertTrue(membership.compare(List.of(column("id", "int")), List.of(column("id", "text"))).isEmpty());
    }

    @Test void fieldFailuresExposeContextAndOriginalCause() {
        var cause = new IllegalStateException("bad data");
        var broken = ModelDiffer.<Column, String>of(Column::key, Comparator.naturalOrder(),
                List.of(Field.of("broken", column -> { throw cause; })));
        var failure = assertThrows(IllegalStateException.class, () -> broken.compare(
                List.of(column("id", "int")), List.of(column("id", "int"))));
        assertTrue(failure.getMessage().contains("broken"));
        assertTrue(failure.getMessage().contains("id"));
        assertSame(cause, failure.getCause());
    }

    @Test void containerCollectionsAreImmutableAndDefinitionIsDefensivelyCopied() {
        var fields = new ArrayList<Field<Column, ?>>(List.of(Field.of("type", Column::type)));
        var local = ModelDiffer.of(Column::key, Comparator.naturalOrder(), fields);
        fields.clear();
        var result = local.compare(List.of(column("id", "int")), List.of(column("id", "text")));
        assertEquals(1, result.modified().size());
        assertThrows(UnsupportedOperationException.class, () -> result.changes().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.modified().getFirst().fields().clear());
    }

    @Test void changeRecordRejectsInvalidStates() {
        assertThrows(IllegalArgumentException.class, () -> new Change<>("x", ChangeType.ADDED, "old", "new", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Change<>("x", ChangeType.MODIFIED, "old", "new", List.of()));
    }

    @Test void sortedMergeMatchesMaterializedResult() {
        var before = List.of(column("a", "int"), column("c", "int"), column("e", "int"));
        var after = List.of(column("b", "int"), column("c", "text"), column("d", "int"));
        var changes = new ArrayList<Change<Column, String>>();
        differ.compareSorted(before.iterator(), after.iterator(), changes::add);
        assertEquals(differ.compare(before, after), new ChangeSet<>(changes));
    }

    @Test void sortedMergeUsesOnlyOneLookaheadPerInputAndDoesNotReadPastConsumerFailure() {
        var beforeReads = new AtomicInteger();
        var afterReads = new AtomicInteger();
        var before = countingIterator(List.of(column("a", "int"), column("c", "int")), beforeReads);
        var after = countingIterator(List.of(column("b", "int"), column("d", "int")), afterReads);
        var stop = new IllegalStateException("stop");
        assertSame(stop, assertThrows(IllegalStateException.class,
                () -> differ.compareSorted(before, after, change -> {
                    assertEquals("a", change.key());
                    assertEquals(1, beforeReads.get());
                    assertEquals(1, afterReads.get());
                    throw stop;
                })));
        assertEquals(1, beforeReads.get());
        assertEquals(1, afterReads.get());
    }

    @Test void sortedMergeRejectsDuplicatesAndDescendingKeysAfterTheEmittedPrefix() {
        var emitted = new ArrayList<Change<Column, String>>();
        assertThrows(IllegalArgumentException.class, () -> differ.compareSorted(
                List.of(column("b", "int"), column("a", "int")).iterator(),
                Collections.emptyIterator(), emitted::add));
        assertEquals(List.of("b"), emitted.stream().map(Change::key).toList());
        assertThrows(IllegalArgumentException.class, () -> differ.compareSorted(Collections.emptyIterator(),
                List.of(column("a", "int"), column("a", "int")).iterator(), ignored -> { }));
    }

    @Test void reversedComparatorWorksForBothModes() {
        var reverse = ModelDiffer.<Column, String>of(Column::key, Comparator.reverseOrder(), List.of());
        var after = List.of(column("z", "int"), column("a", "int"));
        var changes = new ArrayList<Change<Column, String>>();
        reverse.compareSorted(Collections.emptyIterator(), after.iterator(), changes::add);
        assertEquals(List.of("z", "a"), changes.stream().map(Change::key).toList());
        assertEquals(new ChangeSet<>(changes), reverse.compare(List.of(), after));
    }

    @Test void projectsEachKeyOncePerModelInEachMode() {
        var calls = new AtomicInteger();
        var counting = ModelDiffer.<Column, String>of(model -> { calls.incrementAndGet(); return model.key(); },
                Comparator.naturalOrder(), List.of());
        var before = List.of(column("a", "int"), column("b", "int"));
        var after = List.of(column("a", "text"));
        counting.compare(before, after);
        assertEquals(3, calls.get());
        calls.set(0);
        counting.compareSorted(before.iterator(), after.iterator(), ignored -> { });
        assertEquals(3, calls.get());
    }

    private Iterator<Column> countingIterator(List<Column> values, AtomicInteger reads) {
        var delegate = values.iterator();
        return new Iterator<>() {
            @Override public boolean hasNext() { return delegate.hasNext(); }
            @Override public Column next() { reads.incrementAndGet(); return delegate.next(); }
        };
    }
}
