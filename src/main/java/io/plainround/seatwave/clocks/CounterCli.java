package io.plainround.seatwave.clocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BinaryOperator;

/**
 * Counters on five replicas, merged in every order: the grow-only counter, two merges that look
 * reasonable and are wrong, last-writer-wins on a plain number, the counter that can go down, and
 * why that counter cannot guard a floor.
 */
public final class CounterCli {

  private static final List<String> REPLICAS = List.of("r1", "r2", "r3", "r4", "r5");
  private static final long[] INCREMENTS = {1_200, 800, 450, 300, 250};
  private static final long COUNT_BEFORE_RACE = 100;
  private static final long[] UPS = {500, 300, 200};
  private static final long[] DOWNS = {120, 40, 90};

  private CounterCli() {}

  /** Prints the three parts. */
  public static void main(String[] arguments) {
    List<GrowOnlyCounter> states = new ArrayList<>();
    for (int replicaIndex = 0; replicaIndex < REPLICAS.size(); replicaIndex++) {
      states.add(
          GrowOnlyCounter.zero().increment(REPLICAS.get(replicaIndex), INCREMENTS[replicaIndex]));
    }
    System.out.println(
        "PART 1. A fan count on five replicas: r1 to r5 counted 1,200, 800, 450, 300 and 250"
            + " increments (3,000)");
    printMergeRule("grow-only counter (larger tally per replica)", GrowOnlyCounter::merge, states);
    printMergeRule("add the tallies on merge", CounterCli::addOnMerge, states);
    printMergeRule("keep the copy that arrived last", (held, arriving) -> arriving, states);
    long phoneSaw = COUNT_BEFORE_RACE;
    long laptopSaw = COUNT_BEFORE_RACE;
    long lastWriterWins = Math.max(phoneSaw + 1, laptopSaw + 1);
    System.out.printf(
        Locale.ROOT,
        "  last writer wins on a plain number: both replicas read %d, add 1, write %d; kept %d,"
            + " true %d (1 increment lost)%n",
        COUNT_BEFORE_RACE,
        COUNT_BEFORE_RACE + 1,
        lastWriterWins,
        COUNT_BEFORE_RACE + 2);

    System.out.println(
        "\nPART 2. A counter that also goes down: fans mark and unmark 'interested' on r1 to r3");
    List<PositiveNegativeCounter> pnStates = new ArrayList<>();
    for (int replicaIndex = 0; replicaIndex < UPS.length; replicaIndex++) {
      String replicaId = REPLICAS.get(replicaIndex);
      pnStates.add(
          PositiveNegativeCounter.zero()
              .increment(replicaId, UPS[replicaIndex])
              .decrement(replicaId, DOWNS[replicaIndex]));
    }
    List<PositiveNegativeCounter> pnTwice = new ArrayList<>(pnStates);
    pnTwice.addAll(pnStates);
    TreeSet<Long> pnValues = new TreeSet<>();
    int pnOrders = 0;
    for (List<PositiveNegativeCounter> order : permutations(pnTwice)) {
      pnValues.add(
          order.stream()
              .reduce(PositiveNegativeCounter.zero(), PositiveNegativeCounter::merge)
              .value());
      pnOrders++;
    }
    System.out.printf(
        Locale.ROOT,
        "  ups 500, 300, 200; downs 120, 40, 90; every state delivered twice, %d orders:"
            + " values %s%n",
        pnOrders,
        pnValues);

    System.out.println("\nPART 3. The same counter as 'seats left' in a pool of 1");
    PositiveNegativeCounter pool = PositiveNegativeCounter.zero().increment("r1", 1);
    // Each replica checks "value is at least 1" against its own copy, which is true on both.
    PositiveNegativeCounter onFirst = pool.decrement("r1", 1);
    PositiveNegativeCounter onSecond = pool.decrement("r2", 1);
    System.out.printf(
        Locale.ROOT,
        "  r1 sees %d and sells one; r2 sees %d and sells one; after the merge: %d seats left%n",
        pool.value(),
        pool.value(),
        onFirst.merge(onSecond).value());
  }

  private static void printMergeRule(
      String name, BinaryOperator<GrowOnlyCounter> merge, List<GrowOnlyCounter> states) {
    TreeSet<Long> once = new TreeSet<>();
    for (List<GrowOnlyCounter> order : permutations(states)) {
      once.add(order.stream().reduce(GrowOnlyCounter.zero(), merge).value());
    }
    // Each order, then every state again in reverse: a duplicate of each, late, as a retry would.
    TreeSet<Long> duplicated = new TreeSet<>();
    for (List<GrowOnlyCounter> order : permutations(states)) {
      List<GrowOnlyCounter> withRepeats = new ArrayList<>(order);
      withRepeats.addAll(order.reversed());
      duplicated.add(withRepeats.stream().reduce(GrowOnlyCounter.zero(), merge).value());
    }
    System.out.printf(
        Locale.ROOT,
        "  %-46s 120 orders: values %s; each state twice: values %s%n",
        name,
        abbreviate(once),
        abbreviate(duplicated));
  }

  /** A wrong merge on purpose: it counts a copy's tallies again every time it arrives. */
  private static GrowOnlyCounter addOnMerge(GrowOnlyCounter held, GrowOnlyCounter arriving) {
    Map<String, Long> next = new TreeMap<>(held.tallies());
    arriving.tallies().forEach((replicaId, tally) -> next.merge(replicaId, tally, Long::sum));
    return new GrowOnlyCounter(next);
  }

  private static String abbreviate(TreeSet<Long> values) {
    if (values.size() <= 4) {
      return values.toString();
    }
    return String.format(
        Locale.ROOT, "%d different, from %d to %d", values.size(), values.first(), values.last());
  }

  /**
   * Every order of {@code items}, as lists; n items give n! orders, so keep n small (the module
   * uses at most 6, which is 720 orders).
   */
  static <T> List<List<T>> permutations(List<T> items) {
    if (items.isEmpty()) {
      return List.of(List.of());
    }
    List<List<T>> orders = new ArrayList<>();
    for (int position = 0; position < items.size(); position++) {
      List<T> rest = new ArrayList<>(items);
      T chosen = rest.remove(position);
      for (List<T> tail : permutations(rest)) {
        List<T> order = new ArrayList<>();
        order.add(chosen);
        order.addAll(tail);
        orders.add(order);
      }
    }
    return orders;
  }
}
