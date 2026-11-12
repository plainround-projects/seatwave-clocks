package io.plainround.seatwave.clocks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins the module's invariant: replicas that have seen the same set of updates hold the same cart
 * and the same counters, whatever the order and however often each update arrived.
 */
class ConvergenceTest {

  private static final String CART = "cart/0042";
  private static final long SEED = 25;
  private static final int RANDOM_ORDERS = 1_000;
  private static final List<String> REPLICAS = List.of("r1", "r2", "r3", "r4", "r5");
  private static final List<Long> AMOUNTS = List.of(1_200L, 800L, 450L, 300L, 250L);

  @Test
  void everyOrderOfTheCartVersionsGivesTheSameSiblingsAndSeats() {
    List<CartVersion> updates = cartUpdates();
    List<CartVersion> withDuplicate = new ArrayList<>(updates);
    withDuplicate.add(updates.get(1));
    Set<CartReplica> finals = new HashSet<>();
    Set<Set<String>> seatsSeen = new HashSet<>();

    for (List<CartVersion> order : CounterCli.permutations(withDuplicate)) {
      CartReplica replica = deliverAll(order);
      finals.add(new CartReplica("any", CART, replica.siblings()));
      seatsSeen.add(replica.mergedContents().seats());
    }

    assertThat(finals).hasSize(1);
    assertThat(seatsSeen).containsExactly(Set.of("A-7", "A-9", "A-10"));
  }

  @Test
  void aRandomOrderWithRepeatsOfManyUpdatesConvergesToo() {
    List<CartVersion> updates = cartUpdates();
    List<CartVersion> withRepeats = new ArrayList<>(updates);
    withRepeats.addAll(updates);
    CartReplica reference = deliverAll(updates);
    Random random = new Random(SEED);

    for (int orderIndex = 0; orderIndex < RANDOM_ORDERS; orderIndex++) {
      List<CartVersion> shuffled = new ArrayList<>(withRepeats);
      Collections.shuffle(shuffled, random);

      assertThat(deliverAll(shuffled).holdsSameVersionsAs(reference)).isTrue();
    }
  }

  @Test
  void aSeatRemovedOnOneDeviceStaysRemovedWhenMergedWithAConcurrentVersion() {
    CartContents start = CartContents.empty().add("A-7", "phone-1").add("A-8", "phone-2");
    CartContents phone = start.add("A-9", "phone-3");
    CartContents laptop = start.remove("A-8");

    assertThat(phone.merge(laptop).seats()).containsExactly("A-7", "A-9");
    assertThat(laptop.merge(phone).seats()).containsExactly("A-7", "A-9");
  }

  @Test
  void aConcurrentAddOfARemovedSeatSurvivesTheRemoval() {
    CartContents start = CartContents.empty().add("A-8", "phone-1");
    CartContents removedOnLaptop = start.remove("A-8");
    CartContents addedAgainOnPhone = start.add("A-8", "phone-2");

    assertThat(removedOnLaptop.merge(addedAgainOnPhone).seats()).containsExactly("A-8");
  }

  @Test
  void everyOrderOfBothCountersWithADuplicateGivesOneValue() {
    List<GrowOnlyCounter> grow = new ArrayList<>();
    List<PositiveNegativeCounter> upAndDown = new ArrayList<>();
    for (int replicaIndex = 0; replicaIndex < REPLICAS.size(); replicaIndex++) {
      String replicaId = REPLICAS.get(replicaIndex);
      long amount = AMOUNTS.get(replicaIndex);
      grow.add(GrowOnlyCounter.zero().increment(replicaId, amount));
      upAndDown.add(
          PositiveNegativeCounter.zero().increment(replicaId, amount).decrement(replicaId, 1));
    }
    grow.add(grow.getFirst());
    upAndDown.add(upAndDown.getFirst());
    Set<Long> growValues = new HashSet<>();
    Set<Long> upAndDownValues = new HashSet<>();

    for (List<GrowOnlyCounter> order : CounterCli.permutations(grow)) {
      growValues.add(order.stream().reduce(GrowOnlyCounter.zero(), GrowOnlyCounter::merge).value());
    }
    for (List<PositiveNegativeCounter> order : CounterCli.permutations(upAndDown)) {
      upAndDownValues.add(
          order.stream()
              .reduce(PositiveNegativeCounter.zero(), PositiveNegativeCounter::merge)
              .value());
    }

    long increments = AMOUNTS.stream().mapToLong(Long::longValue).sum();
    assertThat(growValues).containsExactly(increments);
    assertThat(upAndDownValues).containsExactly(increments - REPLICAS.size());
  }

  /**
   * Five versions of one cart from three replicas: a first write; on top of it, concurrently, the
   * phone's add (r1), the laptop's removal of A-8 (r2) and a tablet's removal and re-add of A-7
   * (r3); and the phone's second add on top of its first.
   */
  private static List<CartVersion> cartUpdates() {
    CartReplica first = CartReplica.empty("r1", CART);
    CartContents twoSeats = CartContents.empty().add("A-7", "phone-1").add("A-8", "phone-2");
    CartVersion start = first.newVersion(VectorClock.empty(), twoSeats, 0).orElseThrow();
    first = first.receive(start);
    CartReplica laptopSide = CartReplica.empty("r2", CART).receive(start);
    CartReplica tabletSide = CartReplica.empty("r3", CART).receive(start);

    CartVersion phone =
        first.newVersion(first.context(), twoSeats.add("A-9", "phone-3"), 1).orElseThrow();
    CartVersion laptop =
        laptopSide.newVersion(laptopSide.context(), twoSeats.remove("A-8"), 1).orElseThrow();
    CartReplica afterPhone = first.receive(phone);
    CartVersion phoneAgain =
        afterPhone
            .newVersion(afterPhone.context(), phone.contents().add("A-10", "phone-4"), 2)
            .orElseThrow();
    CartVersion tablet =
        tabletSide
            .newVersion(tabletSide.context(), twoSeats.remove("A-7").add("A-7", "tablet-1"), 1)
            .orElseThrow();
    return List.of(start, phone, laptop, phoneAgain, tablet);
  }

  private static CartReplica deliverAll(List<CartVersion> order) {
    CartReplica replica = CartReplica.empty("r4", CART);
    for (CartVersion version : order) {
      replica = replica.receive(version);
    }
    return replica;
  }
}
