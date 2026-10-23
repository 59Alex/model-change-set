# Java 27 baseline

Version **0.2.0** requires **Java 27** to build and run. The public model records,
comparison methods, result ordering, null handling, and duplicate-key policy keep
their 0.1.0 semantics. The minimum JVM is intentionally different: applications
running Java 17, 21, or 25 must upgrade their runtime before adopting 0.2.0.

## Stable features used

The migration adopts useful features available in Java 27; it does not claim they
were all introduced in that release.

| Feature | Where it is used | Why it fits |
| --- | --- | --- |
| `HashSet.newHashSet(expectedElements)`; stable since Java 19 | `ModelDiffer` validates unique field names | The number of configured fields is known. The factory expresses expected entries directly and accounts for the hash table's load factor. |
| Sequenced collections; stable since Java 21 | Result-list examples and regression tests use `getFirst()`, `getLast()`, and `reversed()` | Reports already have a documented key order. Consumers can inspect endpoints or traverse in reverse without making and mutating a copy. |
| Instance main methods; stable since Java 25 | `SchemaDiff` example has `void main()` | The example does not use command-line arguments or static state. The launcher creates the instance, removing an unused arguments parameter and utility-class constructor. |

The public return type stays `List`: it already extends `SequencedCollection`,
and replacing it would unnecessarily break callers using indexed access.
`reversed()` provides a traversal view; it does not reverse additions/removals or
swap the before/after snapshots. Its mutators remain unsupported for these
immutable result lists. Empty endpoints throw `NoSuchElementException`.

Sources: [HashSet factory API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/HashSet.html#newHashSet(int)),
[SequencedCollection API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/SequencedCollection.html),
and [instance main methods](https://docs.oracle.com/en/java/javase/25/language/compact-source-files-instance-main-methods.html).

## Migration

1. Install a complete JDK 27 and select it for Gradle and the application runtime.
2. Upgrade the dependency to `io.github.59alex:model-change-set:0.2.0`.
3. Rebuild and run the application's existing snapshot-comparison checks. No
   changes to `ModelDiffer.of`, `compare`, `compareSorted`, or the result records
   are required for ordinary callers.
4. If a tool directly invokes the **example's** old static `main(String[])` by
   reflection, use the Java launcher or `./gradlew runExample` instead. The
   example entry point is not part of the published library artifact.

There is no multi-release fallback jar for older JVMs. The Gradle publication
metadata and class-file target must agree with the Java 27 baseline. No Maven
Central availability is implied by these coordinates; local installation is
described in the README.

## Deliberate boundaries

No preview language feature, preview API, incubator module, or `--enable-preview`
flag is required. A newer runtime can bring VM improvements independently of this
source code; this library does not claim a measured throughput or GC improvement
without a benchmark of the caller's workload.

Virtual threads and structured concurrency do not improve this synchronous,
in-memory comparison algorithm by themselves. Snapshot acquisition and blocking
I/O belong to the caller. The library therefore starts no threads and changes no
scheduling contract. Stream gatherers are not used: the existing two-input merge
already states its ordering, lookahead, failure, and memory boundaries directly.

Records remain records, and `Change` keeps its existing shape. A sealed hierarchy
or a pattern switch would add migration cost or syntax without simplifying the
current three-way enum validation and report formatting. Preview-only features
are excluded even when they are available in the selected JDK.

## Validation

The existing suite covers semantic behavior and sorted-merge failure boundaries.
Additional tests cover sequenced endpoints, reverse traversal, unmodifiable reverse
views, and empty reports. `runExample` exercises the instance-main launcher.
Run the complete checks on the selected JDK without preview flags:

```sh
./gradlew clean build runExample
```

The release checks also inspect class-file and publication JVM versions so that a
successful compile cannot conceal an older published runtime requirement.
