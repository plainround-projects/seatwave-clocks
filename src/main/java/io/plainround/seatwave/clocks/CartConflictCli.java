package io.plainround.seatwave.clocks;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The cart conflict of Sections 2 and 7, run: a phone and a laptop change {@code cart/0042} through
 * two replicas within the replication delay. Prints every clock after every event, classifies the
 * versions, compares last-writer-wins with keeping siblings, merges with and without removal
 * markers, and then shows what makes siblings and clocks grow.
 */
public final class CartConflictCli {

  private static final String CART = "cart/0042";
  private static final String PHONE_REPLICA = "r1";
  private static final String LAPTOP_REPLICA = "r2";
  private static final long REPLICATION_DELAY_MILLIS = 50;
  private static final long FIRST_WRITE_MILLIS = 0;
  private static final long PHONE_WRITE_MILLIS = 1_000;
  private static final long LAPTOP_WRITE_MILLIS = 1_030;
  private static final long CLOCK_SKEW_MILLIS = 200;

  private static final int BLIND_WRITES = 10;
  private static final int WRITES_FOR_SIZE = 200;
  private static final List<String> HOME_REPLICAS = List.of("r1", "r2", "r3", "r4", "r5");
  private static final int BYTES_PER_COUNTER = 8;

  private CartConflictCli() {}

  /** Prints the five parts. */
  public static void main(String[] arguments) {
    System.out.printf(
        Locale.ROOT,
        "CART CONFLICT: %s on replicas %s (phone) and %s (laptop), replication %d ms%n",
        CART,
        PHONE_REPLICA,
        LAPTOP_REPLICA,
        REPLICATION_DELAY_MILLIS);
    CartReplica phoneSide = CartReplica.empty(PHONE_REPLICA, CART);
    CartReplica laptopSide = CartReplica.empty(LAPTOP_REPLICA, CART);

    System.out.println("\nPART 1. Every clock after every event (real time in ms)");
    CartContents twoSeats = CartContents.empty().add("A-7", "phone-1").add("A-8", "phone-2");
    CartVersion first =
        phoneSide.newVersion(phoneSide.context(), twoSeats, FIRST_WRITE_MILLIS).orElseThrow();
    phoneSide = phoneSide.receive(first);
    print(FIRST_WRITE_MILLIS, "phone writes [A-7, A-8] through r1", phoneSide, laptopSide);
    laptopSide = laptopSide.receive(first);
    print(FIRST_WRITE_MILLIS + REPLICATION_DELAY_MILLIS, "r2 receives it", phoneSide, laptopSide);

    // Both devices read their replica, change the cart, and write back with what they read.
    CartContents phoneCart = phoneSide.mergedContents().add("A-9", "phone-3");
    CartVersion phoneVersion =
        phoneSide.newVersion(phoneSide.context(), phoneCart, PHONE_WRITE_MILLIS).orElseThrow();
    phoneSide = phoneSide.receive(phoneVersion);
    print(PHONE_WRITE_MILLIS, "phone adds A-9 through r1", phoneSide, laptopSide);
    CartContents laptopCart = laptopSide.mergedContents().remove("A-8");
    CartVersion laptopVersion =
        laptopSide.newVersion(laptopSide.context(), laptopCart, LAPTOP_WRITE_MILLIS).orElseThrow();
    laptopSide = laptopSide.receive(laptopVersion);
    print(LAPTOP_WRITE_MILLIS, "laptop removes A-8 through r2", phoneSide, laptopSide);
    laptopSide = laptopSide.receive(phoneVersion);
    print(
        PHONE_WRITE_MILLIS + REPLICATION_DELAY_MILLIS,
        "r2 receives the phone's",
        phoneSide,
        laptopSide);
    phoneSide = phoneSide.receive(laptopVersion);
    print(
        LAPTOP_WRITE_MILLIS + REPLICATION_DELAY_MILLIS,
        "r1 receives the laptop's",
        phoneSide,
        laptopSide);
    System.out.printf(
        Locale.ROOT,
        "  the two replicas hold the same versions: %b%n",
        phoneSide.holdsSameVersionsAs(laptopSide));

    System.out.println("\nPART 2. The versions compared");
    printComparison("first write", first, "phone's write", phoneVersion);
    printComparison("first write", first, "laptop's write", laptopVersion);
    printComparison("phone's write", phoneVersion, "laptop's write", laptopVersion);

    System.out.println("\nPART 3. What the fan sees, by resolution rule");
    for (long laptopSkew : new long[] {CLOCK_SKEW_MILLIS, -CLOCK_SKEW_MILLIS}) {
      CartVersion stampedLaptop =
          new CartVersion(
              CART,
              laptopVersion.clock(),
              laptopVersion.contents(),
              laptopVersion.wallClockMillis() + laptopSkew);
      CartVersion kept =
          List.of(phoneVersion, stampedLaptop).stream()
              .max(Comparator.comparingLong(CartVersion::wallClockMillis))
              .orElseThrow();
      System.out.printf(
          Locale.ROOT,
          "  last writer wins, r2's clock %+d ms: stamps %d against %d, keeps the %s's %s;"
              + " lost: %s%n",
          laptopSkew,
          phoneVersion.wallClockMillis(),
          stampedLaptop.wallClockMillis(),
          kept == phoneVersion ? "phone" : "laptop",
          kept.contents().seats(),
          kept == phoneVersion ? "the removal of A-8" : "the add of A-9");
    }
    SortedSet<String> naiveUnion = new TreeSet<>(phoneVersion.contents().seats());
    naiveUnion.addAll(laptopVersion.contents().seats());
    System.out.printf(
        Locale.ROOT, "  keep both, merge by union of seats: %s (A-8 comes back)%n", naiveUnion);
    CartContents merged = phoneSide.mergedContents();
    System.out.printf(
        Locale.ROOT,
        "  keep both, merge with removal markers: %s (markers %s)%n",
        merged.seats(),
        merged.removalMarkers());
    VectorClock contextRead = phoneSide.context();
    CartVersion resolved =
        phoneSide
            .newVersion(contextRead, merged, LAPTOP_WRITE_MILLIS + REPLICATION_DELAY_MILLIS)
            .orElseThrow();
    phoneSide = phoneSide.receive(resolved);
    System.out.printf(
        Locale.ROOT,
        "  the next write sends the context %s, gets %s and replaces both: %d sibling, %s %s%n",
        contextRead,
        resolved.clock(),
        phoneSide.siblings().size(),
        resolved.clock(),
        phoneSide.mergedContents().seats());

    printSiblingGrowth();
    printClockSize();
  }

  private static void print(
      long realMillis, String what, CartReplica phoneSide, CartReplica laptopSide) {
    System.out.printf(
        Locale.ROOT,
        "  %5d  %-32s r1 holds %s%n         %-32s r2 holds %s%n",
        realMillis,
        what,
        describe(phoneSide),
        "",
        describe(laptopSide));
  }

  private static String describe(CartReplica replica) {
    if (replica.siblings().isEmpty()) {
      return "nothing";
    }
    StringBuilder text = new StringBuilder();
    for (CartVersion sibling : replica.siblings()) {
      text.append(text.isEmpty() ? "" : " | ").append(sibling.clock()).append(' ');
      text.append(sibling.contents().seats());
    }
    return text.toString();
  }

  private static void printComparison(
      String firstName, CartVersion firstVersion, String secondName, CartVersion secondVersion) {
    System.out.printf(
        Locale.ROOT,
        "  %-14s %-12s is %-10s the %-14s %s%n",
        firstName,
        firstVersion.clock(),
        firstVersion.clock().compareTo(secondVersion.clock()),
        secondName,
        secondVersion.clock());
  }

  private static void printSiblingGrowth() {
    System.out.printf(
        Locale.ROOT,
        "%nPART 4. %d writes of one seat each, each through the next home replica in turn%n",
        BLIND_WRITES);
    CartReplica withContext = CartReplica.empty(PHONE_REPLICA, CART);
    CartReplica blind = CartReplica.empty(PHONE_REPLICA, CART);
    int blindRefused = 0;
    for (int writeIndex = 0; writeIndex < BLIND_WRITES; writeIndex++) {
      String throughReplica = HOME_REPLICAS.get(writeIndex % HOME_REPLICAS.size());
      String seat = "B-" + (writeIndex + 1);
      String tag = "device-" + (writeIndex + 1);
      // The store is modeled as one replica that has received everything, renamed per write.
      CartReplica coordinator = new CartReplica(throughReplica, CART, withContext.siblings());
      CartContents next = withContext.mergedContents().add(seat, tag);
      withContext =
          withContext.receive(coordinator.newVersion(withContext.context(), next, 0).orElseThrow());
      // A blind write sends no context, as a client that never read the cart would.
      CartReplica blindCoordinator = new CartReplica(throughReplica, CART, blind.siblings());
      CartContents alone = CartContents.empty().add(seat, tag);
      Optional<CartVersion> accepted = blindCoordinator.newVersion(VectorClock.empty(), alone, 0);
      if (accepted.isPresent()) {
        blind = blind.receive(accepted.get());
      } else {
        blindRefused++;
      }
    }
    System.out.printf(
        Locale.ROOT,
        "  writes that send the context they read: %d sibling, clock %s, seats %d%n",
        withContext.siblings().size(),
        withContext.context(),
        withContext.mergedContents().seats().size());
    System.out.printf(
        Locale.ROOT,
        "  blind writes (no context): %d siblings %s, %d refused (read first)%n",
        blind.siblings().size(),
        blind.context(),
        blindRefused);
  }

  private static void printClockSize() {
    System.out.printf(
        Locale.ROOT,
        "%nPART 5. Clock size after %d writes of one cart (8 bytes per counter plus the id)%n",
        WRITES_FOR_SIZE);
    VectorClock byReplica = VectorClock.empty();
    VectorClock byDevice = VectorClock.empty();
    for (int writeIndex = 0; writeIndex < WRITES_FOR_SIZE; writeIndex++) {
      byReplica = byReplica.increment(HOME_REPLICAS.get(writeIndex % HOME_REPLICAS.size()));
      byDevice = byDevice.increment(String.format(Locale.ROOT, "device-%03d", writeIndex));
    }
    System.out.printf(
        Locale.ROOT,
        "  one entry per home replica (r1 to r5): %3d entries, %5d bytes%n",
        byReplica.size(),
        bytes(byReplica));
    System.out.printf(
        Locale.ROOT,
        "  one entry per writing device:          %3d entries, %5d bytes%n",
        byDevice.size(),
        bytes(byDevice));
  }

  private static int bytes(VectorClock clock) {
    return clock.counters().keySet().stream()
        .mapToInt(replicaId -> replicaId.length() + BYTES_PER_COUNTER)
        .sum();
  }
}
