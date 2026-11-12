package io.plainround.seatwave.clocks;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A grow-only counter (G-Counter), a CRDT: one tally per replica, each changed only by its own
 * replica; the value is the sum; two copies merge by taking the larger tally per replica. This is
 * Module 20's fan count ("per-replica tallies, summed") with its merge written down. Immutable and
 * therefore thread-safe.
 *
 * @param tallies each replica's own count of increments; every tally is 1 or more
 */
public record GrowOnlyCounter(Map<String, Long> tallies) {

  /** Rejects blank replica ids and tallies below 1, and copies into a sorted map. */
  public GrowOnlyCounter {
    Objects.requireNonNull(tallies, "tallies");
    tallies.forEach((replicaId, tally) -> {
      Objects.requireNonNull(replicaId, "replica id");
      Objects.requireNonNull(tally, "tally of " + replicaId);
      if (replicaId.isBlank() || tally < 1) {
        throw new IllegalArgumentException(
            "a tally needs a replica id and a count of 1 or more, was " + replicaId + "=" + tally);
      }
    });
    tallies = Collections.unmodifiableSortedMap(new TreeMap<>(tallies));
  }

  /** A counter at zero. */
  public static GrowOnlyCounter zero() {
    return new GrowOnlyCounter(Map.of());
  }

  /**
   * The counter after {@code replicaId} counts {@code amount} more; the amount must be positive.
   */
  public GrowOnlyCounter increment(String replicaId, long amount) {
    if (amount < 1) {
      throw new IllegalArgumentException("a grow-only counter only grows, amount was " + amount);
    }
    TreeMap<String, Long> next = new TreeMap<>(tallies);
    next.merge(replicaId, amount, Math::addExact);
    return new GrowOnlyCounter(next);
  }

  /**
   * Both copies' knowledge in one: the larger tally per replica. Taking the larger, never the sum,
   * is what makes merging the same copy twice harmless: a tally only ever grows, so the larger one
   * already includes the smaller.
   */
  public GrowOnlyCounter merge(GrowOnlyCounter other) {
    TreeMap<String, Long> next = new TreeMap<>(tallies);
    other.tallies.forEach((replicaId, tally) -> next.merge(replicaId, tally, Math::max));
    return new GrowOnlyCounter(next);
  }

  /** The count: the sum of every replica's tally. */
  public long value() {
    return tallies.values().stream().mapToLong(Long::longValue).reduce(0L, Math::addExact);
  }
}
