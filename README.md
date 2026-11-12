# seatwave-clocks

Clocks and ordering for SeatWave's cart store: a vector clock, a Lamport clock and a hybrid logical clock; the cart as an observed-remove set that keeps concurrent versions as siblings and merges them without losing a seat; a grow-only and a positive-negative counter that converge in every delivery order; and four programs that make each idea visible. This is the project of Module 25, "Clocks and ordering", of the course System Design Fundamentals.

Every number the programs print comes from fixed inputs (a 50 ms replication delay, a 200 ms skew, scripted clock readings), so a run is the same on every machine. They describe the model, not a real deployment. No Docker, no database and no container is involved.

## Prerequisites

- JDK 25 (the Gradle toolchain finds it; built and tested with Eclipse Temurin 25.0.2, where 25.0.5+8 is the current release on 12 November 2026).
- Nothing else: `./gradlew` downloads Gradle 9.9.1 (checksum pinned) and the test libraries.

## Dependencies (each checked on Maven Central and the Gradle site on 12 November 2026; OSV listed no vulnerability)

| What | Coordinates | Version | License | Why the JDK alone is not enough |
|---|---|---|---|---|
| Gradle wrapper | `gradle-9.9.1-bin.zip`, checksum compared with the published one | 9.9.1 | Apache-2.0 | one build tool version for every reader |
| JUnit | `org.junit:junit-bom` (test) | 6.1.3 | EPL-2.0 | the test runner |
| AssertJ | `org.assertj:assertj-core` (test) | 3.27.7 | Apache-2.0 | readable assertions |
| Spotless, google-java-format | `com.diffplug.spotless`, `com.google.googlejavaformat:google-java-format` | 8.11.0, 2.0.0 | Apache-2.0 | the formatter |
| JaCoCo | `org.jacoco` | 0.8.16 | EPL-2.0 | line coverage, floor 0.80 |

The library itself uses only the JDK (`java.util`, `java.time`).

## Commands and their real output

Build, test, format check and run all four programs: `./gradlew clean check skewRun orderingRun cartRun counterRun` (Gradle 9.9.1, JDK 25.0.2, Apple-silicon laptop, 12 November 2026). The build part:

```
> Task :test
> Task :jacocoTestCoverageVerification
> Task :spotlessJava
> Task :spotlessJavaCheck
> Task :spotlessCheck
> Task :check
> Task :skewRun
...
BUILD SUCCESSFUL in 2s
12 actionable tasks: 12 executed
```

58 tests (JUnit counts each parameter set of a parameterized test), 0 failed. Line coverage of everything except the `*Cli` programs: 98.6 percent (207 of 210 lines), against a floor of 0.80. The programs are run by hand and their output is checked in the module.

The programs, one task each:

| Task | Program | What it prints |
|---|---|---|
| `./gradlew -q skewRun` | `ClockSkewCli` | drift, one NTP-style exchange, last writer wins by wall clock under skew, a stepped clock |
| `./gradlew -q orderingRun` | `OrderingCli` | one trace stamped by wall clock, Lamport, vector and hybrid clocks |
| `./gradlew -q cartRun` | `CartConflictCli` | the phone and laptop conflict: siblings, the context, the observed-remove merge, clock size |
| `./gradlew -q counterRun` | `CounterCli` | counters merged in every order, wrong merges, the floor problem |

For example, `./gradlew -q counterRun`:

```
PART 1. A fan count on five replicas: r1 to r5 counted 1,200, 800, 450, 300 and 250 increments (3,000)
  grow-only counter (larger tally per replica)   120 orders: values [3000]; each state twice: values [3000]
  add the tallies on merge                       120 orders: values [3000]; each state twice: values [6000]
  keep the copy that arrived last                120 orders: values 5 different, from 250 to 1200; each state twice: values 5 different, from 250 to 1200
  last writer wins on a plain number: both replicas read 100, add 1, write 101; kept 101, true 102 (1 increment lost)

PART 2. A counter that also goes down: fans mark and unmark 'interested' on r1 to r3
  ups 500, 300, 200; downs 120, 40, 90; every state delivered twice, 720 orders: values [750]

PART 3. The same counter as 'seats left' in a pool of 1
  r1 sees 1 and sells one; r2 sees 1 and sells one; after the merge: -1 seats left
```

The other three programs print similar scenarios; their full output is in the module, Section 8. All four were run again through Gradle on 12 November 2026 and match the output captured when the module was written, byte for byte.

## Mutation checks

Every test that guards a rule was checked by removing the rule in a scratch copy and running `./gradlew test --continue`. A mutant is killed when a test fails. Full output: `module-prompts/Generated/_tools/module-25-outputs/mutants.txt` of the course folder.

| Rule removed | Tests that fail |
|---|---|
| M1 the G-Counter merge takes the larger tally (mutant: sums them) | 3: `ConvergenceTest` (every order of both counters), two in `CounterValidationTest` |
| M2 a removal leaves markers (mutant: deletes the seat's adds) | 3: `CartContentsTest` (a marker stays), two in `ConvergenceTest` |
| M3 a replica ignores a version it has already seen (mutant: stores it) | 3: `CartReplicaTest` (a duplicate changes nothing), two in `ConvergenceTest` |
| M4 a Lamport receive takes the maximum (mutant: ignores the received counter) | 2: `LamportClockTest` (jumps past a larger counter; ordered after the send) |
| M5 a Lamport receive adds one (mutant: the receive ties with the send) | 2: `LamportClockTest` (both receive tests) |
| M6 a hybrid tick never goes back with the wall clock (mutant: follows a wall clock stepping backward) | 2: `HybridTimestampTest` (keeps going forward; physical part never ahead of the largest reading) |
| M7 a hybrid receive takes the largest physical part of both (mutant: ignores the sender's) | 2: `HybridTimestampTest` (a receive from the future; the skew bound) |
| M8 a vector-clock comparison reports CONCURRENT (mutant: calls concurrent clocks ordered) | 1: `VectorClockTest.reportsConcurrentWhenEachHasACounterTheOtherLacks` |
| M9 `newVersion` refuses a stale context (mutant: accepts it) | 1: `CartReplicaTest.refusesANewVersionWhoseContextLacksAWriteThisReplicaMade` |
| M10 a seat with every add marked is hidden (mutant: visible while any tag exists) | 4: two in `CartContentsTest`, two in `ConvergenceTest` |

## Repeated and relocated runs

12 November 2026, Gradle 9.9.1, JDK 25.0.2, Apple-silicon laptop:

- `./gradlew cleanTest test` 40 times in a row beside three busy loops (load average 8.07 before and 9.20 after): 40 passed, 0 failed.
- `./gradlew clean check` from a copy at a path of 354 characters: passed. From a copy in a folder whose names contain spaces: passed.

## Project structure

```
src/main/java/io/plainround/seatwave/clocks/   the library (8 types) and the four *Cli programs
src/test/java/io/plainround/seatwave/clocks/   unit tests, one class per type or rule, and ConvergenceTest
build.gradle.kts  gradle/libs.versions.toml  gradle.lockfile  settings-gradle.lockfile
ARCHITECTURE.md  CHANGELOG.md  LICENSE
```

## What is simplified

- A cart key keeps its siblings in memory in one `CartReplica` value; there is no store, no network and no replication protocol. Replication is a call to `receive`.
- One vector-clock entry per replica, so `newVersion` refuses a stale context (a client that has not seen this replica's own latest write). Dotted version vectors would remove the refusal; they are described in the module, not coded.
- Add tags are chosen by the caller (`phone-1`); production would have the server assign them. Removal markers are never pruned.
- Clock readings are arguments (`HybridTimestamp.tick(wallClockMillis)`) or scripted `java.time.Clock` values, so no program reads this machine's clock.
- Not coded, described in the module: the signed opaque context token, the sibling ceiling, the clock offset probe and its two alerts, and the shadow-mode rollout. The estimates (8 conflicts a second, about 200 bytes of tags per cart, 2.5 GB of metadata) are still estimates; counting them needs a shadow-mode run in production.

## No CI on purpose

There is no `.github/` folder and no workflow. `./gradlew clean check` is the gate and runs on a laptop in under a minute.
