# model-change-set 0.3.0 — usage guide

[English](en.md) · [Русский](ru.md)

[Versions](../README.md) · [README](../../README.md) · [Java features](java.en.md)

Compare keyed model snapshots with explicit field policies. Receive deterministic additions, removals, and modifications, or merge sorted inputs one change at a time.

## Capabilities at a glance

| Capability | Use case | Example |
| --- | --- | --- |
| 1. Install and prepare the example | Use a complete JDK 17 or newer. Supported runtimes are LTS 17, 21, 25 and non-LTS 27. The library has no runtime dependencies. | [Code and result](#1-install-and-prepare-the-example) |
| 2. Compare snapshots and control ordering | Choose a stable key, a total key comparator, and the fields that define a meaningful change. Input collections can arrive in any order. | [Code and result](#2-compare-snapshots-and-control-ordering) |
| 3. Define equality and handle nulls | Field.of uses Objects.deepEquals: it supports nulls and array contents; other values use their own equals method. Field.comparing lets the domain decide what counts as equal. | [Code and result](#3-define-equality-and-handle-nulls) |
| 4. Filter results and compare membership | Use added, removed, modified, and isEmpty to choose the next application action. An empty field list compares identities only; an explicit subset ignores irrelevant fields. | [Code and result](#4-filter-results-and-compare-membership) |
| 5. Merge sorted sources incrementally | compareSorted accepts two iterators whose keys are strictly increasing under the same comparator, then calls a synchronous consumer for each change. It does not index both snapshots. | [Code and result](#5-merge-sorted-sources-incrementally) |
| 6. Validate identities and understand limits | Duplicate keys and ambiguous comparators fail explicitly. The comparison direction and change shape remain visible in every result. | [Code and result](#6-validate-identities-and-understand-limits) |
| 7. Run the complete example and diagnose failures | Download Example.java and save it at the repository root after building version 0.3.0. The following POSIX-shell commands use the ordinary jar, not the sources or Javadoc jar. | [Code and result](#7-run-the-complete-example-and-diagnose-failures) |

## 1. Install and prepare the example

Use a complete JDK 17 or newer. Supported runtimes are LTS 17, 21, 25 and non-LTS 27. The library has no runtime dependencies.

> **Local artifact, not a Central release**
>
> The commands below build the source and install version 0.3.0 in your local Maven repository. These coordinates do not imply that an artifact is available from Maven Central.

```bash
git clone https://github.com/59Alex/model-change-set.git
cd model-change-set
./gradlew build publishToMavenLocal -PtestJavaVersion=17
```

Run from a checkout containing version 0.3.0; Gradle needs access to its distribution and build dependencies.

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}
dependencies {
    implementation("io.github.59alex:model-change-set:0.3.0")
}
```

Add to the consuming application's build.gradle.kts after local installation.

The Java snippets below form one tutorial: add the imports before your Example class, declare Column inside the class, and run the snippets in order inside its main method. Later sections explicitly reuse differ, before, after, fields, and changes from section 2. The downloadable Example.java already combines all snippets.

```java
import io.github.alex59.modelchanges.Change;
import io.github.alex59.modelchanges.ChangeSet;
import io.github.alex59.modelchanges.ChangeType;
import io.github.alex59.modelchanges.Field;
import io.github.alex59.modelchanges.ModelDiffer;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// Declare this record inside your Example class.
record Column(String key, String type, boolean nullable) {}
```

Shared imports and model declaration. The key remains stable across snapshots.

Downloadable file: examples/model-change-set/Example.java. It uses only the library jar and the JDK; section 7 shows how to compile and run it.

## 2. Compare snapshots and control ordering

Choose a stable key, a total key comparator, and the fields that define a meaningful change. Input collections can arrive in any order.

```java
List<Field<Column, ?>> fields = List.of(
    Field.of("type", Column::type),
    Field.of("nullable", Column::nullable)
);
var differ = ModelDiffer.<Column, String>of(
    Column::key, Comparator.naturalOrder(), fields
);
var before = List.of(
    new Column("orders.note", "text", true),
    new Column("orders.id", "bigint", false),
    new Column("orders.legacy", "text", true)
);
var after = List.of(
    new Column("orders.id", "bigint", false),
    new Column("orders.created_at", "timestamp", false),
    new Column("orders.note", "text", false)
);
var changes = differ.compare(before, after);
changes.changes().forEach(change -> {
    System.out.println(change.type() + " " + change.key());
    change.fields().forEach(field -> System.out.println(
        "  " + field.name() + ": " + field.before() + " -> " + field.after()
    ));
});
```

After the shared declarations from section 1, place this code inside main.

```text
ADDED orders.created_at
REMOVED orders.legacy
MODIFIED orders.note
  nullable: true -> false
```

Expected output. The unchanged orders.id is omitted.

- ADDED means the key occurs only in after. before is null, after contains the complete model, and fields is empty.
- REMOVED means the key occurs only in before. after is null and fields is empty.
- MODIFIED means the key occurs in both snapshots and at least one configured field differs. FieldChange holds the field name and original before/after values.
- Changes follow the key comparator. Changed fields follow their definition order. Renaming a key appears as a removal plus an addition.

```java
var reorderedBefore = new ArrayList<>(before);
Collections.reverse(reorderedBefore);
System.out.println("same report=" + changes.equals(
    differ.compare(reorderedBefore, after)
));
var reverseDiffer = ModelDiffer.<Column, String>of(
    Column::key, Comparator.reverseOrder(), fields
);
System.out.println("descending=" + reverseDiffer.compare(before, after)
    .changes().stream().map(Change::key).toList());
```

Reuses before, after, fields, and changes above. Reordering input leaves the report unchanged; a different comparator changes report order.

```text
same report=true
descending=[orders.note, orders.legacy, orders.created_at]
```

Expected output.

## 3. Define equality and handle nulls

Field.of uses Objects.deepEquals: it supports nulls and array contents; other values use their own equals method. Field.comparing lets the domain decide what counts as equal.

```java
record Price(String key, BigDecimal amount) {}
var amount = Field.<Price, BigDecimal>comparing(
    "amount", Price::amount,
    (a, b) -> a == null ? b == null
        : b != null && a.compareTo(b) == 0
);
var priceDiffer = ModelDiffer.<Price, String>of(
    Price::key, Comparator.naturalOrder(), List.of(amount)
);
System.out.println("field=" + amount.name());
System.out.println("same amount=" + priceDiffer.compare(
    List.of(new Price("price", new BigDecimal("1.0"))),
    List.of(new Price("price", new BigDecimal("1.00")))
).isEmpty());
var priceChange = priceDiffer.compare(
    List.of(new Price("price", null)),
    List.of(new Price("price", new BigDecimal("1.00")))
).modified().get(0).fields().get(0);
System.out.println(priceChange.name() + ": "
    + priceChange.before() + " -> " + priceChange.after());
```

Uses the shared imports. Price is a separate local record; monetary scale is ignored, but null remains distinct from a number.

```text
field=amount
same amount=true
amount: null -> 1.00
```

Expected output. Reports preserve original values; the equality policy does not normalize them.

```java
var nullToEmpty = differ.compare(
    List.of(new Column("note", null, true)),
    List.of(new Column("note", "", true))
);
var absentToNull = differ.compare(
    List.of(), List.of(new Column("note", null, true))
);
System.out.println("null-to-empty modified=" + nullToEmpty.modified().size());
System.out.println("absent-to-null added=" + absentToNull.added().size());

record Blob(String key, byte[] bytes) {}
var blobDiffer = ModelDiffer.<Blob, String>of(
    Blob::key, Comparator.naturalOrder(), List.of(Field.of("bytes", Blob::bytes))
);
System.out.println("equal arrays=" + blobDiffer.compare(
    List.of(new Blob("blob", new byte[] {1, 2})),
    List.of(new Blob("blob", new byte[] {1, 2}))
).isEmpty());
```

Reuses differ from section 2, then creates an independent array-valued model.

```text
null-to-empty modified=1
absent-to-null added=1
equal arrays=true
```

Expected output: a null field is not an absent model or an empty string.

> **Callback contract**
>
> Custom equivalence receives nulls unchanged. Extractors and policies should be pure and stable; if you share a differ across threads, its callbacks and input data must also be safe to share. Default equality does not recursively discover arbitrary model fields.

## 4. Filter results and compare membership

Use added, removed, modified, and isEmpty to choose the next application action. An empty field list compares identities only; an explicit subset ignores irrelevant fields.

```java
System.out.println("added=" + changes.added().stream().map(Change::key).toList());
System.out.println("removed=" + changes.removed().stream().map(Change::key).toList());
System.out.println("modified=" + changes.modified().stream().map(Change::key).toList());
System.out.println("has changes=" + !changes.isEmpty());

var membershipOnly = ModelDiffer.<Column, String>of(
    Column::key, Comparator.naturalOrder(), List.of()
);
System.out.println("membership changes=" + membershipOnly.compare(before, after)
    .changes().size());
System.out.println("same identity=" + membershipOnly.compare(
    List.of(new Column("id", "int", true)),
    List.of(new Column("id", "bigint", false))
).isEmpty());

var typeOnly = ModelDiffer.<Column, String>of(
    Column::key, Comparator.naturalOrder(), List.of(Field.of("type", Column::type))
);
System.out.println("ignored nullable=" + typeOnly.compare(
    List.of(new Column("id", "int", true)),
    List.of(new Column("id", "int", false))
).isEmpty());
```

Reuses changes, before, and after from section 2. Each filtered result is an unmodifiable list in key order.

```text
added=[orders.created_at]
removed=[orders.legacy]
modified=[orders.note]
has changes=true
membership changes=2
same identity=true
ignored nullable=true
```

Expected output. The identity-only comparison excludes the nullable modification.

> **Container immutability is not a deep copy**
>
> ChangeSet copies its list, and Change copies its field list. Models, keys, and field values remain references to caller-owned objects. Use immutable snapshots if you retain a report for later review.

## 5. Merge sorted sources incrementally

compareSorted accepts two iterators whose keys are strictly increasing under the same comparator, then calls a synchronous consumer for each change. It does not index both snapshots.

```java
var emitted = new ArrayList<Change<Column, String>>();
differ.compareSorted(
    before.stream().sorted(Comparator.comparing(Column::key)).iterator(),
    after.stream().sorted(Comparator.comparing(Column::key)).iterator(),
    emitted::add
);
System.out.println("same merge=" + changes.equals(new ChangeSet<>(emitted)));

var prefix = new ArrayList<String>();
try {
    differ.compareSorted(
        List.of(
            new Column("b", "int", false),
            new Column("a", "int", false)
        ).iterator(),
        Collections.emptyIterator(),
        change -> prefix.add(change.key())
    );
} catch (IllegalArgumentException failure) {
    System.out.println(failure.getMessage());
}
System.out.println("emitted prefix=" + prefix);
```

Reuses differ, before, after, and changes. The first part sorts small in-memory lists for the demonstration; the second deliberately supplies invalid order.

```text
same merge=true
Keys in before are not strictly increasing: b then a
emitted prefix=[b]
```

Expected output. The removal for b was emitted before the next key revealed the invalid order.

- For a large database snapshot, provide a genuinely ordered cursor instead of sorting an unbounded stream in memory. The library does not execute SQL or manage database snapshots.
- The merge retains one current model from each source and one change. A collector such as emitted::add retains the whole report; use a sink that does not retain it when memory is the concern.
- A late duplicate, descending key, field callback failure, or consumer exception can leave an emitted prefix. Consumer exceptions stop processing immediately and propagate.
- The caller closes iterators and database cursors. The library does not create a transaction, roll back consumer side effects, start threads, or apply reactive backpressure.

> **Complete report or incremental output**
>
> Use compare when the caller should receive one complete ChangeSet or an exception. Use compareSorted when ordered input and incremental output matter. Neither option makes user callbacks transactional.

## 6. Validate identities and understand limits

Duplicate keys and ambiguous comparators fail explicitly. The comparison direction and change shape remain visible in every result.

```java
try {
    differ.compare(
        List.of(new Column("x", "int", false), new Column("x", "int", false)),
        List.of()
    );
} catch (IllegalArgumentException failure) {
    System.out.println(failure.getMessage());
}
try {
    differ.compare(List.of(), List.of(new Column(null, "int", false)));
} catch (NullPointerException failure) {
    System.out.println(failure.getMessage());
}
var insensitive = ModelDiffer.<Column, String>of(
    Column::key, String.CASE_INSENSITIVE_ORDER, fields
);
try {
    insensitive.compare(
        List.of(new Column("a", "int", false)),
        List.of(new Column("A", "int", false))
    );
} catch (IllegalArgumentException failure) {
    System.out.println(failure.getMessage());
}
try {
    changes.changes().clear();
} catch (UnsupportedOperationException failure) {
    System.out.println("result is unmodifiable");
}
```

Reuses differ, fields, and changes. These failures are intentional and handled by the example.

```text
Duplicate key in before: x
Null key in after
Comparator treats unequal keys as identical: a and A
result is unmodifiable
```

Expected output.

```java
var broken = ModelDiffer.<Column, String>of(
    Column::key, Comparator.naturalOrder(),
    List.of(Field.of("broken", column -> {
        throw new IllegalArgumentException("bad value");
    }))
);
try {
    broken.compare(
        List.of(new Column("x", "int", false)),
        List.of(new Column("x", "int", false))
    );
} catch (IllegalStateException failure) {
    System.out.println(failure.getMessage());
    System.out.println("cause=" + failure.getCause().getMessage());
}
try {
    new Change<>("x", ChangeType.MODIFIED,
        new Column("x", "int", false),
        new Column("x", "bigint", false),
        List.of());
} catch (IllegalArgumentException failure) {
    System.out.println(failure.getMessage());
}
```

A failed field projection preserves its cause. Directly constructed records also validate their shape.

```text
Cannot compare field 'broken' at key x
cause=bad value
Invalid change shape for MODIFIED
```

Expected output.

- Keys and models must be non-null. Keys must be stable and have a total comparator consistent with equals. Equal models do not make duplicate identities valid.
- A comparator that returns zero for unequal keys is rejected when the collision is encountered, including across snapshots. The library cannot prove transitivity of an arbitrary comparator.
- Field names must be non-null, nonblank, and unique within a differ. Field extractors and equality policies must be non-null. Duplicate definitions fail before comparison starts.
- Change validates ADDED: before null, after present, fields empty; REMOVED: before present, after null, fields empty; MODIFIED: both models present and fields nonempty. Key and type are required.
- For n old models, m new models, and f fields, compare costs O(n log n + m log m + min(n,m) × f) plus result construction and uses O(n + m + result size) memory. Callback and comparison costs are assumed constant.
- compareSorted costs O(n + m + min(n,m) × f) and O(f) working memory, excluding model sizes and data retained by the consumer. It does not make an unsorted source bounded-memory.

> **Scope**
>
> The library reports differences. It does not detect renames, classify backward compatibility, generate or execute database migrations, persist an audit log, serialize reports, or traverse arbitrary object graphs.

## 7. Run the complete example and diagnose failures

Download Example.java and save it at the repository root after building version 0.3.0. The following POSIX-shell commands use the ordinary jar, not the sources or Javadoc jar.

```bash
mkdir -p example-classes
javac --release 17 -cp build/libs/model-change-set-0.3.0.jar \
  -d example-classes Example.java
java -cp example-classes:build/libs/model-change-set-0.3.0.jar Example
```

The example executes every Java scenario above. On Windows, use a semicolon instead of a colon in the runtime classpath.

```text
All examples completed.
```

The final line after the expected outputs from sections 2–6.

```bash
./gradlew clean build runExample -PtestJavaVersion=17
./gradlew clean build runExample -PtestJavaVersion=21
./gradlew clean build runExample -PtestJavaVersion=25
./gradlew clean build runExample -PtestJavaVersion=27
```

Run the library's tests and bundled schema example on each installed JDK. Compilation always targets the Java 17 API and bytecode.

- package io.github.alex59.modelchanges does not exist: check that the ordinary 0.3.0 jar exists and appears on javac's classpath, or install it locally before resolving the Gradle dependency.
- UnsupportedClassVersionError: check java -version and the selected jar. Version 0.3.0 targets Java 17; a Java 27-only artifact from the earlier baseline is not interchangeable.
- No matching Java installation found: install the JDK requested by testJavaVersion and expose it to Gradle toolchain discovery. A JRE without javac cannot compile the project.
- Duplicate key in before/after: fix the input or choose a true compound identity. Do not silently drop one model to hide a collision.
- Keys in before/after are not strictly increasing: sort by the same comparator as the differ. Discard or compensate for any partial external report before retrying.
- Cannot compare field: inspect getCause(), then correct the projection, equivalence policy, or data. Exceptions from the result consumer propagate without this field wrapper.

## Downloadable examples

- [Example.java](examples/Example.java)

[MIT](../../LICENSE)
