# Architecture

## Purpose and boundary

An application has two finite snapshots of a logical model and needs to understand
what changed. `model-change-set` maps stable identities to explicit projections and
produces an ordered report. The application owns snapshot acquisition, consistent
reads, domain policies, report serialization, and any subsequent action.

The library runs inside its caller's JVM. There are no deployed services, storage
containers, threads, queues, or framework lifecycle hooks.

Version 0.2.0 requires Java 27 and preserves the same comparison contracts. The
[Java migration notes](JAVA-27.md) describe the stable APIs adopted and the
features intentionally left out of this synchronous core.

```text
Application / CI check
  | before + after models, identity and field policies
  v
ModelDiffer (in-process Java library)
  | ordered ChangeSet, or synchronous Change callbacks
  v
Application report / review / conditional refresh
```

The arrows represent method calls. All model acquisition and persistence remain
outside this boundary. The text diagram uses no external renderer.

## Components and critical flow

| Component | Responsibility |
| --- | --- |
| `ModelDiffer` | Validate identities, align snapshots, compare configured fields, order results |
| `Field` | Name one typed projection and implement its equivalence policy |
| `Change` / `FieldChange` | Represent one added, removed, or modified model and its values |
| `ChangeSet` | Hold the complete immutable result and provide views by change type |

For `compare(before, after)`:

1. Consume the old snapshot into a key-ordered index. Reject null models/keys,
   duplicate identities, and comparator-equivalent unequal keys.
2. Build the new snapshot index using the same rules. No result is returned while
   either snapshot is still being indexed.
3. Merge the indexes. An old-only key is removed; a new-only key is added.
4. For a shared identity, evaluate configured fields in definition order. Keep
   original values for each field whose policy reports a difference.
5. Omit unchanged identities and return an immutable list of changes.

`compareSorted` uses the same merge over caller-provided sorted iterators, without
the indexes. It validates each next key before continuing and emits each change
directly to the consumer. It retains one current entry per input. This avoids
whole-snapshot memory at the cost of a caller-visible partial result on late errors.

## Invariants

- One identity occurs at most once within either snapshot.
- The comparator is a stable total order consistent with key equality. Known
  comparator-zero/equality mismatches fail explicitly; the library cannot prove
  comparator transitivity or detect all inconsistent user implementations.
- Each reported identity has exactly one change type.
- A modification contains at least one configured field difference. Additions and
  removals contain a complete model reference and an empty field list.
- Reordering valid unsorted snapshots does not change the report.
- Null field values remain distinct from absence of a model.
- Key extractors are evaluated once per model. Projections run only for matched
  identities, once per side and field. User policies must be pure and stable.
- Library-owned result containers cannot be mutated. Referenced user objects are
  not cloned; callers must preserve snapshot immutability for durable reports.

## Decisions and trade-offs

| Decision | Benefit and cost | Reconsider when |
| --- | --- | --- |
| Explicit projections instead of reflection | Stable public semantics; no hidden technical fields. Callers declare relevant fields. | A separate schema adapter can provide equivalent explicit metadata. |
| Ordered indexes for unsorted input | Deterministic reports and early duplicate rejection. Memory scales with both snapshots. | Inputs already have trustworthy key order; use the merge API. |
| Reject duplicate keys | No silent winner or arbitrary source/target pairing. Caller must repair or group ambiguous input. | A domain requires multi-value identities; model that grouping explicitly. |
| Separate null field and missing model | Nullable attributes do not masquerade as deletions. Change consumers must handle absent before/after models by type. | No planned change; this is a core contract. |
| Synchronous core without Reactor | Usable in batch, web, CLI, and reactive apps without framework dependencies. Caller controls scheduling and I/O. | Add an adapter if reactive source backpressure becomes a concrete requirement. |
| References rather than deep clones | Works with any model and avoids hidden copy costs. Mutable models can alter retained report values. | A serializer/snapshot adapter can offer a defined copy contract. |

## Failure and recovery

| Failure | State and observable effect | Recovery | Verification |
| --- | --- | --- | --- |
| Duplicate identity | `compare` returns no result; `compareSorted` may have emitted a prefix | Correct the snapshot or choose a proper compound identity, then rerun | Duplicates tested on both sides, including identical model instances |
| Comparator collision | Explicit exception instead of conflating distinct models | Use a total order consistent with equality | Within-snapshot and cross-snapshot collisions tested |
| Null model/key | Input validation exception | Repair data; nullable fields themselves are supported | Input/model/key and null-vs-empty tests |
| Field extractor/policy throws | Exception includes field/key context and original cause | Fix callback or data; rerun with an idempotent report sink | Cause and context assertions |
| Sorted input goes backwards | Merge aborts after any already emitted prefix | Sort correctly; discard/replace partial report | Descending order and prefix behavior test |
| Report consumer throws | Merge stops immediately without reading more entries | Caller closes resources and handles partial side effects | Counting iterators verify stop and bounded lookahead |
| Concurrent mutation of user models | Behavior is outside the immutable-snapshot contract | Read a stable snapshot or copy before comparing | Explicit limitation; library does not lock user state |

The library has no transaction boundary around callbacks. Use application-owned
staging or a transaction when partial report persistence would be unsafe.

## What to know

Identity defines alignment; projections define meaning; comparator order defines
presentation. The library guarantees neither migration safety nor durable audit
storage. Its useful boundary is a reproducible report with explicit input errors
and a bounded-memory path when callers can provide sorted snapshots.
