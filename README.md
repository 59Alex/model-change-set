# model-change-set

[![CI](https://github.com/59Alex/model-change-set/actions/workflows/ci.yml/badge.svg)](https://github.com/59Alex/model-change-set/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-64748b.svg)](LICENSE)

**Turn two model snapshots into a deterministic, field-aware change set.**

Compare schema catalogs, metadata definitions, configuration inventories, or API
models by stable identity. Choose the fields that matter and their equivalence
rules; get ordered additions, removals, and modifications with before/after values.

Java 27+. No runtime dependencies, reflection, annotations, or framework setup.
Version 0.2.0 moves the minimum JDK from 17 to 27 and uses stable Java APIs only;
see the [migration notes](docs/JAVA-27.md).

## Compare two snapshots

```java
import io.github.alex59.modelchanges.Field;
import io.github.alex59.modelchanges.ModelDiffer;
import java.util.Comparator;
import java.util.List;

record Column(String qualifiedName, String type, boolean nullable) {}

var differ = ModelDiffer.<Column, String>of(
    Column::qualifiedName,
    Comparator.naturalOrder(),
    List.of(Field.of("type", Column::type), Field.of("nullable", Column::nullable))
);

var before = List.of(
    new Column("orders.id", "bigint", false),
    new Column("orders.note", "text", true),
    new Column("orders.legacy", "text", true)
);
var after = List.of(
    new Column("orders.id", "bigint", false),
    new Column("orders.note", "text", false),
    new Column("orders.created_at", "timestamp", false)
);

var changes = differ.compare(before, after);
changes.changes().forEach(change -> {
    System.out.println(change.type() + " " + change.key());
    change.fields().forEach(field -> System.out.println(
        "  " + field.name() + ": " + field.before() + " -> " + field.after()
    ));
});
```

```text
ADDED orders.created_at
REMOVED orders.legacy
MODIFIED orders.note
  nullable: true -> false
```

Run the complete [example](src/examples/java/io/github/alex59/modelchanges/examples/SchemaDiff.java):

```sh
./gradlew runExample
```

`changes.added()`, `changes.removed()`, and `changes.modified()` return immutable
filtered lists. `changes.isEmpty()` is useful for a CI check or a conditional refresh.
An empty field definition list compares membership only.

The returned lists also support sequenced access and reverse-order report views:

```java
if (!changes.isEmpty()) {
    var firstKey = changes.changes().getFirst().key();
    var lastKey = changes.changes().getLast().key();
}
var descendingReport = changes.changes().reversed();
```

`reversed()` changes traversal order, not the before/after direction. The view
remains unmodifiable. `getFirst()` and `getLast()` throw `NoSuchElementException`
on an empty list, so check `isEmpty()` when the snapshots may match.

## Decide what “equal” means

Fields use `Objects.deepEquals` by default: nulls and arrays are supported; other
objects use their own `equals`. A custom policy can ignore insignificant formatting
or numeric scale without rewriting the values included in the report:

```java
record Price(String key, java.math.BigDecimal amount) {}

var amount = Field.<Price, java.math.BigDecimal>comparing(
    "amount", Price::amount,
    (a, b) -> a == null ? b == null : b != null && a.compareTo(b) == 0
);
```

This treats `1.0` and `1.00` as equal. Custom policies receive nulls unchanged and
must define their own null semantics. Omit timestamps and other technical fields
from the definition when they should not affect the change set.

## Process sorted inputs without indexing both snapshots

If the caller can provide both snapshots in strictly increasing key order:

```java
differ.compareSorted(beforeIterator, afterIterator, change -> writeReportRow(change));
```

The synchronous merge retains one current model from each iterator and one change.
It reads both inputs once, emits in key order, and stops immediately if the consumer
throws. Iterators and underlying database cursors remain the caller's responsibility
to close. This API does not read a database or manage transactions.

Validation happens as entries arrive. A late duplicate, out-of-order key, extractor
failure, or consumer failure can leave an already emitted prefix. Use `compare`
when the caller must receive either one complete result or an exception. Neither
method makes side effects in user callbacks transactional.

## Contracts

| Concern | Behavior |
| --- | --- |
| Direction | `compare(before, after)`; an identity change is removal plus addition |
| Identity | Non-null stable key; comparator must define a total order consistent with `equals` |
| Duplicates | Rejected in either snapshot, even if the models are equal |
| Comparator collisions | Comparator-equivalent but unequal keys are rejected, including across snapshots |
| Model values | Null models are rejected; null projected field values are allowed |
| Null vs missing | A null field is a value; an absent model is an addition/removal |
| Order | Changes follow key order; changed fields follow definition order |
| Added/removed fields | Empty list; the whole model is available in `after`/`before` |
| Mutability | Result containers are immutable; models, keys, and field values are not deep-copied |
| Concurrency | Differ has no per-call mutable shared state; supplied functions and data must be safe to share |
| Failures | Invalid inputs throw; field failures include field/key context and retain the cause |

For `n` old models, `m` new models, and `f` configured fields:

- `compare`: `O(n log n + m log m + min(n,m) × f)` work, plus result construction;
  `O(n + m + result size)` memory.
- `compareSorted`: `O(n + m + min(n,m) × f)` work and `O(f)` working memory,
  excluding model sizes and any storage retained by the consumer.

These bounds assume constant-cost key comparison, field extraction and equality.
Use immutable snapshots and pure callbacks. The library does not infer renames,
traverse arbitrary object graphs, classify backward compatibility, apply migrations,
or persist an audit log. A schema change report is evidence for a decision, not an
automatic declaration that a database migration is safe.

## Build and use locally

The repository builds a Java library with source and Javadoc jars. To install its
artifact into your local Maven repository:

```sh
./gradlew build publishToMavenLocal
```

Then, in a consuming Gradle project:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}
dependencies {
    implementation("io.github.59alex:model-change-set:0.2.0")
}
```

The command above installs locally; it does not publish to Maven Central. CI tests
Java 27, builds the source/Javadoc artifacts, and runs the schema example. Set
`JAVA_HOME` to a complete JDK 27 installation before running Gradle. Preview flags
are not required.

See [architecture and failure behavior](docs/ARCHITECTURE.md) for the design and
trade-offs. Contributions should include a regression test for any changed contract.

## License

[MIT](LICENSE). The Gradle wrapper retains its upstream Apache 2.0 notices.
