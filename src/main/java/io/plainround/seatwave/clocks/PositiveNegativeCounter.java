package io.plainround.seatwave.clocks;

import java.util.Objects;

/**
 * A counter that can also go down (PN-Counter), a CRDT built from two grow-only counters: one
 * counts increments, the other decrements, and the value is their difference. Each half merges by
 * its own rule, so the pair converges as a G-Counter does. It cannot promise the value stays at or
 * above zero: two replicas may each subtract the last unit at once. Immutable.
 *
 * @param increments every replica's count of ups
 * @param decrements every replica's count of downs
 */
public record PositiveNegativeCounter(GrowOnlyCounter increments, GrowOnlyCounter decrements) {

  /** Rejects missing halves. */
  public PositiveNegativeCounter {
    Objects.requireNonNull(increments, "increments");
    Objects.requireNonNull(decrements, "decrements");
  }

  /** A counter at zero. */
  public static PositiveNegativeCounter zero() {
    return new PositiveNegativeCounter(GrowOnlyCounter.zero(), GrowOnlyCounter.zero());
  }

  /** The counter after {@code replicaId} adds {@code amount}. */
  public PositiveNegativeCounter increment(String replicaId, long amount) {
    return new PositiveNegativeCounter(increments.increment(replicaId, amount), decrements);
  }

  /** The counter after {@code replicaId} subtracts {@code amount}. */
  public PositiveNegativeCounter decrement(String replicaId, long amount) {
    return new PositiveNegativeCounter(increments, decrements.increment(replicaId, amount));
  }

  /** Both halves merged, each by taking the larger tally per replica. */
  public PositiveNegativeCounter merge(PositiveNegativeCounter other) {
    return new PositiveNegativeCounter(
        increments.merge(other.increments), decrements.merge(other.decrements));
  }

  /** Ups minus downs; may be negative. */
  public long value() {
    return Math.subtractExact(increments.value(), decrements.value());
  }
}
