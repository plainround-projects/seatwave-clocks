package io.plainround.seatwave.clocks;

import java.util.Comparator;
import java.util.Objects;

/**
 * A Lamport timestamp held by one node: a counter that goes up by one at every local event and
 * jumps past any counter it receives. Immutable: each operation returns the node's next clock.
 *
 * @param nodeId the node that owns this clock; also the tie-breaker of the total order
 * @param counter the node's current counter; 0 before its first event
 */
public record LamportClock(String nodeId, long counter) {

  /**
   * The total order built on top of the counters: by counter, then by node id. It is consistent
   * with happens-before, and it is invented for concurrent events, which it orders arbitrarily.
   */
  public static final Comparator<LamportClock> TOTAL_ORDER =
      Comparator.comparingLong(LamportClock::counter).thenComparing(LamportClock::nodeId);

  /** Rejects a blank node id and a negative counter. */
  public LamportClock {
    Objects.requireNonNull(nodeId, "nodeId");
    if (nodeId.isBlank() || counter < 0) {
      throw new IllegalArgumentException(
          "a Lamport clock needs a node id and a counter of 0 or more, was "
              + nodeId
              + "="
              + counter);
    }
  }

  /** A node's clock before its first event. */
  public static LamportClock start(String nodeId) {
    return new LamportClock(nodeId, 0);
  }

  /** The clock after a local event or a send: one more than before. */
  public LamportClock tick() {
    return new LamportClock(nodeId, Math.addExact(counter, 1));
  }

  /**
   * The clock after receiving a message stamped {@code receivedCounter}: past both the local and
   * the received counter, so the receive is ordered after the send that caused it.
   */
  public LamportClock receive(long receivedCounter) {
    return new LamportClock(nodeId, Math.addExact(Math.max(counter, receivedCounter), 1));
  }

  @Override
  public String toString() {
    return counter + "@" + nodeId;
  }
}
