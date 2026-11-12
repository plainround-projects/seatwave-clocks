# Changelog

## 1.0.0 (12 November 2026)

- Built from the Module 25 code seed: `VectorClock`, `LamportClock`, `HybridTimestamp`, `CartContents`, `CartVersion`, `CartReplica`, `GrowOnlyCounter`, `PositiveNegativeCounter` and the four programs. The four programs print byte for byte what the seed printed.
- Gradle 9.9.1 wrapper pinned by checksum, Kotlin DSL, version catalog, dependency and settings lockfiles, `-Xlint:all -Werror`, Spotless with google-java-format 2.0.0, JaCoCo with a line floor of 0.80, one `JavaExec` task per program (`skewRun`, `orderingRun`, `cartRun`, `counterRun`).
- Tests for `LamportClock`, `HybridTimestamp` (both receive rules, a wall clock that steps backward, the skew bound), `CartContents` validation and merge, `CartReplica` (including the refusal of a stale context), and the counters' validation, on top of the seed's `VectorClockTest` and `ConvergenceTest`; each rule's test was checked by removing the rule (README, "Mutation checks").
