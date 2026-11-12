package io.plainround.seatwave.clocks;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A vector clock: one counter per replica that has written, read as "how many writes of each
 * replica this version has seen". Immutable and therefore thread-safe. A replica that is absent
 * counts as 0, so the empty clock is the clock of the empty cart that nobody has written yet.
 *
 * @param counters counter per replica id; every id is non-blank and every counter is 1 or more
 */
public record VectorClock(Map<String, Long> counters) {

  /** The four answers a comparison of two vector clocks can give. */
  public enum Ordering {
    /** This clock happened before the other: every counter is less than or equal, one is less. */
    BEFORE,
    /** This clock happened after the other: the mirror of {@link #BEFORE}. */
    AFTER,
    /** Neither saw the other: each has a counter the other lacks. Both versions must be kept. */
    CONCURRENT,
    /** The same set of writes: the same version. */
    EQUAL
  }

  /** Rejects blank replica ids and counters below 1, and copies the map into a sorted one. */
  public VectorClock {
    Objects.requireNonNull(counters, "counters");
    counters.forEach((replicaId, counter) -> {
      Objects.requireNonNull(replicaId, "replica id");
      Objects.requireNonNull(counter, "counter of " + replicaId);
      if (replicaId.isBlank() || counter < 1) {
        throw new IllegalArgumentException(
            "a vector clock entry needs a replica id and a counter of 1 or more, was "
                + replicaId
                + "="
                + counter);
      }
    });
    // Sorted and unmodifiable, so equal clocks print alike and nobody can change one in place.
    counters = Collections.unmodifiableSortedMap(new TreeMap<>(counters));
  }

  /** The clock of a version no replica has written yet. */
  public static VectorClock empty() {
    return new VectorClock(Map.of());
  }

  /** How many writes of {@code replicaId} this clock has seen; 0 if none. */
  public long counterOf(String replicaId) {
    return counters.getOrDefault(replicaId, 0L);
  }

  /** The clock of a new write by {@code replicaId} on top of this one: its own counter plus one. */
  public VectorClock increment(String replicaId) {
    Map<String, Long> next = new TreeMap<>(counters);
    next.merge(replicaId, 1L, Long::sum);
    return new VectorClock(next);
  }

  /**
   * The smallest clock that has seen everything both clocks have seen: the larger counter per
   * replica. Commutative, associative and idempotent, which is why merging never depends on order.
   */
  public VectorClock merge(VectorClock other) {
    Map<String, Long> next = new TreeMap<>(counters);
    other.counters.forEach((replicaId, counter) -> next.merge(replicaId, counter, Math::max));
    return new VectorClock(next);
  }

  /** Compares the two clocks entry by entry; see {@link Ordering} for the four answers. */
  public Ordering compareTo(VectorClock other) {
    boolean hasSmaller = false;
    boolean hasLarger = false;
    TreeSet<String> replicaIds = new TreeSet<>(counters.keySet());
    replicaIds.addAll(other.counters.keySet());
    for (String replicaId : replicaIds) {
      long mine = counterOf(replicaId);
      long theirs = other.counterOf(replicaId);
      hasSmaller |= mine < theirs;
      hasLarger |= mine > theirs;
    }
    if (hasSmaller && hasLarger) {
      return Ordering.CONCURRENT;
    }
    if (hasSmaller) {
      return Ordering.BEFORE;
    }
    return hasLarger ? Ordering.AFTER : Ordering.EQUAL;
  }

  /** Whether this clock has seen every write {@code other} has seen (after or equal). */
  public boolean dominates(VectorClock other) {
    Ordering ordering = compareTo(other);
    return ordering == Ordering.AFTER || ordering == Ordering.EQUAL;
  }

  /** The number of entries, which is the metadata cost Section 10 watches. */
  public int size() {
    return counters.size();
  }

  @Override
  public String toString() {
    StringBuilder text = new StringBuilder("[");
    counters.forEach((replicaId, counter) ->
        text.append(text.length() > 1 ? ", " : "").append(replicaId).append(':').append(counter));
    return text.append(']').toString();
  }
}
