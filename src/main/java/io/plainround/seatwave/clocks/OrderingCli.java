package io.plainround.seatwave.clocks;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One trace of seven events on three replicas of {@code cart/0042}, stamped four ways: by each
 * replica's skewed wall clock, by Lamport clocks, by vector clocks and by hybrid logical clocks.
 * Then four pairs of events, compared by each stamp against what really happened.
 */
public final class OrderingCli {

  /** The kinds of event a replica takes part in. */
  private enum Kind {
    WRITE,
    SEND,
    RECEIVE
  }

  /**
   * One event of the trace.
   *
   * @param label a letter the text refers to
   * @param replicaId the replica where it happens
   * @param trueMillis when it happens in real time, which no replica can read
   * @param kind write, send or receive
   * @param messageId the message sent or received; empty for a write
   * @param description what the event is, for the printout
   */
  private record Event(
      String label,
      String replicaId,
      long trueMillis,
      Kind kind,
      String messageId,
      String description) {}

  /**
   * One event's four stamps.
   *
   * @param event the event
   * @param wallMillis its replica's wall-clock reading
   * @param lamport its Lamport timestamp
   * @param vector its vector clock
   * @param hybrid its hybrid logical clock timestamp
   */
  private record Stamped(
      Event event,
      long wallMillis,
      LamportClock lamport,
      VectorClock vector,
      HybridTimestamp hybrid) {}

  /** Real time 0 is shown as 10,000 ms on a correct clock, so skewed readings stay positive. */
  private static final long WALL_CLOCK_BASE_MILLIS = 10_000;

  /** How far each replica's wall clock is from real time: r2 is behind, r3 is ahead. */
  private static final Map<String, Long> SKEW_MILLIS = Map.of("r1", 0L, "r2", -200L, "r3", 100L);

  private static final List<Event> TRACE =
      List.of(
          new Event("a", "r1", 0, Kind.WRITE, "", "phone adds A-9 through r1"),
          new Event("b", "r1", 1, Kind.SEND, "m1", "r1 sends version a to r2"),
          new Event("c", "r3", 10, Kind.WRITE, "", "laptop removes A-8 through r3"),
          new Event("d", "r2", 51, Kind.RECEIVE, "m1", "r2 receives version a"),
          new Event("e", "r2", 80, Kind.WRITE, "", "phone, having seen a, adds A-10 through r2"),
          new Event("f", "r2", 81, Kind.SEND, "m2", "r2 sends version e to r3"),
          new Event("g", "r3", 131, Kind.RECEIVE, "m2", "r3 receives version e"));

  private static final List<String[]> PAIRS =
      List.of(
          new String[] {"a", "e"},
          new String[] {"a", "c"},
          new String[] {"c", "e"},
          new String[] {"c", "g"});

  private OrderingCli() {}

  /** Prints the stamped trace and the comparison of the four pairs. */
  public static void main(String[] arguments) {
    Map<String, Stamped> stamped = stampTrace();
    System.out.println(
        "ONE TRACE, FOUR CLOCKS: replicas r1, r2, r3 of cart/0042; wall clocks r1 correct,"
            + " r2 200 ms behind, r3 100 ms ahead; replication 50 ms");
    System.out.println(
        "  event  replica  real ms  wall stamp  Lamport  vector clock        hybrid    what");
    for (Stamped one : stamped.values()) {
      System.out.printf(
          Locale.ROOT,
          "  %-5s  %-7s  %7d  %10d  %7d  %-18s  %-8s  %s%n",
          one.event().label(),
          one.event().replicaId(),
          one.event().trueMillis(),
          one.wallMillis(),
          one.lamport().counter(),
          one.vector(),
          one.hybrid(),
          one.event().description());
    }
    System.out.println(
        "\n  pair   really         by wall stamp  by Lamport     by vector clock  by hybrid");
    for (String[] pair : PAIRS) {
      Stamped first = stamped.get(pair[0]);
      Stamped second = stamped.get(pair[1]);
      System.out.printf(
          Locale.ROOT,
          "  %s, %s   %-13s  %-13s  %-13s  %-15s  %s%n",
          pair[0],
          pair[1],
          first.vector().compareTo(second.vector()) == VectorClock.Ordering.CONCURRENT
              ? "concurrent"
              : "a cause of " + pair[1],
          order(Long.compare(first.wallMillis(), second.wallMillis())),
          order(LamportClock.TOTAL_ORDER.compare(first.lamport(), second.lamport())),
          first.vector().compareTo(second.vector()),
          order(first.hybrid().compareTo(second.hybrid())));
    }
  }

  private static Map<String, Stamped> stampTrace() {
    Map<String, LamportClock> lamport = new HashMap<>();
    Map<String, VectorClock> vector = new HashMap<>();
    Map<String, HybridTimestamp> hybrid = new HashMap<>();
    Map<String, Stamped> sent = new HashMap<>();
    Map<String, Stamped> result = new LinkedHashMap<>();
    for (Event event : TRACE) {
      String replicaId = event.replicaId();
      long wallMillis = WALL_CLOCK_BASE_MILLIS + event.trueMillis() + SKEW_MILLIS.get(replicaId);
      LamportClock lamportBefore = lamport.getOrDefault(replicaId, LamportClock.start(replicaId));
      VectorClock vectorBefore = vector.getOrDefault(replicaId, VectorClock.empty());
      HybridTimestamp hybridBefore = hybrid.getOrDefault(replicaId, new HybridTimestamp(0, 0));
      Stamped now;
      if (event.kind() == Kind.RECEIVE) {
        Stamped message = sent.get(event.messageId());
        now =
            new Stamped(
                event,
                wallMillis,
                lamportBefore.receive(message.lamport().counter()),
                vectorBefore.merge(message.vector()).increment(replicaId),
                hybridBefore.receive(message.hybrid(), wallMillis));
      } else {
        now =
            new Stamped(
                event,
                wallMillis,
                lamportBefore.tick(),
                vectorBefore.increment(replicaId),
                hybridBefore.tick(wallMillis));
        if (event.kind() == Kind.SEND) {
          sent.put(event.messageId(), now);
        }
      }
      lamport.put(replicaId, now.lamport());
      vector.put(replicaId, now.vector());
      hybrid.put(replicaId, now.hybrid());
      result.put(event.label(), now);
    }
    return result;
  }

  private static String order(int comparison) {
    if (comparison == 0) {
      return "tie";
    }
    return comparison < 0 ? "first first" : "second first";
  }
}
