package io.plainround.seatwave.clocks;

import java.util.Comparator;

/**
 * A hybrid logical clock timestamp (Kulkarni and colleagues, 2014): the largest physical time this
 * node has heard of, in milliseconds, and a logical counter that orders events which share that
 * millisecond. Immutable; the two operations take the node's physical clock reading as an argument,
 * so the rules can be tested with any clock, skewed or stepped.
 *
 * @param physicalMillis the largest wall-clock reading seen so far, local or received
 * @param logical how many events since {@code physicalMillis} last moved; 0 or more
 */
public record HybridTimestamp(long physicalMillis, long logical)
    implements Comparable<HybridTimestamp> {

  private static final Comparator<HybridTimestamp> ORDER =
      Comparator.comparingLong(HybridTimestamp::physicalMillis)
          .thenComparingLong(HybridTimestamp::logical);

  /** Rejects negative parts. */
  public HybridTimestamp {
    if (physicalMillis < 0 || logical < 0) {
      throw new IllegalArgumentException(
          "a hybrid timestamp has no negative parts, was " + physicalMillis + "." + logical);
    }
  }

  /**
   * The timestamp of a local event or a send. If the wall clock has moved past everything seen,
   * take it and reset the counter; otherwise (a clock that is behind, or the same millisecond) keep
   * the old physical part and count.
   */
  public HybridTimestamp tick(long wallClockMillis) {
    if (wallClockMillis > physicalMillis) {
      return new HybridTimestamp(wallClockMillis, 0);
    }
    return new HybridTimestamp(physicalMillis, Math.addExact(logical, 1));
  }

  /**
   * The timestamp of a receive of {@code received}: larger than both this node's last timestamp and
   * the sender's, and equal to the wall clock when the wall clock is ahead of both.
   */
  public HybridTimestamp receive(HybridTimestamp received, long wallClockMillis) {
    long largestPhysical =
        Math.max(Math.max(physicalMillis, received.physicalMillis), wallClockMillis);
    // The counter continues from whichever timestamp already holds the largest physical part.
    boolean mineHoldsIt = largestPhysical == physicalMillis;
    boolean theirsHoldsIt = largestPhysical == received.physicalMillis;
    long nextLogical;
    if (mineHoldsIt && theirsHoldsIt) {
      nextLogical = Math.max(logical, received.logical) + 1;
    } else if (mineHoldsIt) {
      nextLogical = logical + 1;
    } else if (theirsHoldsIt) {
      nextLogical = received.logical + 1;
    } else {
      nextLogical = 0;
    }
    return new HybridTimestamp(largestPhysical, nextLogical);
  }

  @Override
  public int compareTo(HybridTimestamp other) {
    return ORDER.compare(this, other);
  }

  @Override
  public String toString() {
    return physicalMillis + "." + logical;
  }
}
