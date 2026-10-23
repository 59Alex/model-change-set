# Java support and migration

Version **0.3.0** has a **Java 17 minimum**. The supported LTS runtime matrix is
**Java 17, Java 21, and Java 25**. One library artifact serves all three versions;
there is no version-specific implementation or multi-release jar.

## Compatibility contract

- Production code, tests, and examples compile with `--release 17`. This checks
  both Java language syntax and the Java 17 platform API, even when compiling
  with a newer supported JDK.
- Published class files target Java 17 (major version **61**, minor **0**).
- Gradle module metadata declares a minimum JVM version of **17**.
- Tests and the schema example run on the JDK selected with
  `-PtestJavaVersion=17`, `21`, or `25`; setting only a bytecode target is not a
  substitute for executing the supported runtime matrix.
- Preview features, incubator modules, reflection-based version fallbacks, and
  `--enable-preview` are not required.

Java 27 is not required and is not part of this supported LTS matrix. This document
replaces the [historical Java 27 baseline](JAVA-27.md) from version 0.2.0.

## Build and verify

Install the complete JDKs needed for the selected runs and make them available to
Gradle's toolchain discovery. Select a JDK with the project property:

```sh
./gradlew clean build runExample -PtestJavaVersion=17
./gradlew clean build runExample -PtestJavaVersion=21
./gradlew clean build runExample -PtestJavaVersion=25
```

All three runs compile against the same Java 17 API and use the selected runtime
for tests and the example. CI follows the same LTS matrix. The release checks also
inspect the generated class files and publication metadata to catch a target
version mismatch.

## Migrate from 0.1.0 or 0.2.0

Update the dependency to `io.github.59alex:model-change-set:0.3.0`, rebuild, and run
the application's existing comparison checks. Maven Central availability is not
implied by the coordinates; see the README for local installation.

`ModelDiffer.of`, `compare`, `compareSorted`, and the public result records keep
their signatures and semantics. Added/removed/modified classifications, stable
ordering, null handling, duplicate-key rejection, and sorted-merge failure
boundaries are unchanged. Field definitions are still copied before validation;
later mutation of the caller's list cannot change the differ.

If application source adopted Java 21 list methods such as `getFirst()` or
`reversed()`, that application source still needs Java 21 or a corresponding
rewrite when targeting Java 17. The library's own source and examples use the
Java 17 `List` API. The runnable schema example again exposes
`public static void main(String[] args)`.

## Language and architecture choices

Records, switch expressions, local-variable type inference, immutable list
factories, and `Stream.toList()` are all available within the Java 17 baseline.
They retain concise, typed code without requiring callers to upgrade their JVM.
The newer `HashSet.newHashSet` factory is replaced with the Java 17 constructor;
uniqueness checks still operate on the defensive copy of the field definitions.

The algorithm remains synchronous and dependency-free. It starts no threads and
performs no I/O. Virtual threads, structured concurrency, and stream gatherers
are unnecessary for its two-input merge and would change compatibility or
execution assumptions without solving a problem inside this boundary.

Result lists and their iterators remain unmodifiable. Reverse iteration changes
the report's presentation order, never the direction of a change. Tests cover
this contract as well as empty reports, duplicate identities, field policies,
deterministic ordering, and bounded-lookahead merge behavior.
