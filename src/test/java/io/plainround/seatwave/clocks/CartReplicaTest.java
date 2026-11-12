package io.plainround.seatwave.clocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pins how a replica writes, receives and keeps siblings. */
class CartReplicaTest {

  private static final String CART = "cart/0042";
  private static final CartContents ONE_SEAT = CartContents.empty().add("A-7", "phone-1");

  @Test
  void aNewVersionIsTheContextPlusOneForThisReplica() {
    CartReplica replica = CartReplica.empty("r1", CART);

    CartVersion version = replica.newVersion(VectorClock.empty(), ONE_SEAT, 10).orElseThrow();

    assertThat(version.clock()).isEqualTo(new VectorClock(Map.of("r1", 1L)));
    assertThat(version.wallClockMillis()).isEqualTo(10);
  }

  @Test
  void refusesANewVersionWhoseContextLacksAWriteThisReplicaMade() {
    CartReplica replica = CartReplica.empty("r1", CART);
    CartVersion first = replica.newVersion(VectorClock.empty(), ONE_SEAT, 1).orElseThrow();
    replica = replica.receive(first);

    assertThat(replica.newVersion(VectorClock.empty(), ONE_SEAT, 2)).isEmpty();
  }

  @Test
  void acceptsANewVersionWhoseContextHasEverythingThisReplicaWrote() {
    CartReplica replica = CartReplica.empty("r1", CART);
    CartVersion first = replica.newVersion(VectorClock.empty(), ONE_SEAT, 1).orElseThrow();
    replica = replica.receive(first);

    CartVersion second = replica.newVersion(replica.context(), ONE_SEAT, 2).orElseThrow();

    assertThat(second.clock().counterOf("r1")).isEqualTo(2);
  }

  @Test
  void aWriteMadeOnAnotherReplicaDoesNotBlockThisReplica() {
    CartReplica r2 = CartReplica.empty("r2", CART);
    CartVersion fromR2 = r2.newVersion(VectorClock.empty(), ONE_SEAT, 1).orElseThrow();
    CartReplica r1 = CartReplica.empty("r1", CART).receive(fromR2);

    assertThat(r1.newVersion(VectorClock.empty(), ONE_SEAT, 2)).isPresent();
  }

  @Test
  void aDuplicateOrAnOlderVersionChangesNothing() {
    CartReplica replica = CartReplica.empty("r1", CART);
    CartVersion first = replica.newVersion(VectorClock.empty(), ONE_SEAT, 1).orElseThrow();
    replica = replica.receive(first);
    CartVersion second = replica.newVersion(replica.context(), ONE_SEAT, 2).orElseThrow();
    replica = replica.receive(second);

    assertThat(replica.receive(second)).isEqualTo(replica);
    assertThat(replica.receive(first)).isEqualTo(replica);
    assertThat(replica.siblings()).containsExactly(second);
  }

  @Test
  void aNewerVersionReplacesTheOnesItHasSeen() {
    CartReplica replica = CartReplica.empty("r1", CART);
    CartVersion first = replica.newVersion(VectorClock.empty(), ONE_SEAT, 1).orElseThrow();
    replica = replica.receive(first);
    CartVersion second = replica.newVersion(replica.context(), ONE_SEAT, 2).orElseThrow();

    assertThat(replica.receive(second).siblings()).containsExactly(second);
  }

  @Test
  void concurrentVersionsAreBothKeptAndTheContextCoversBoth() {
    CartVersion fromR1 =
        CartReplica.empty("r1", CART).newVersion(VectorClock.empty(), ONE_SEAT, 1).orElseThrow();
    CartVersion fromR2 =
        CartReplica.empty("r2", CART).newVersion(VectorClock.empty(), ONE_SEAT, 1).orElseThrow();

    CartReplica replica = CartReplica.empty("r3", CART).receive(fromR1).receive(fromR2);

    assertThat(replica.siblings()).hasSize(2);
    assertThat(replica.context()).isEqualTo(new VectorClock(Map.of("r1", 1L, "r2", 1L)));
  }

  @Test
  void rejectsAVersionOfAnotherKeyAndSiblingsOfAnotherKey() {
    CartVersion other =
        CartReplica.empty("r1", "cart/0001")
            .newVersion(VectorClock.empty(), ONE_SEAT, 1)
            .orElseThrow();

    assertThatThrownBy(() -> CartReplica.empty("r1", CART).receive(other))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cart/0001");
    assertThatThrownBy(() -> new CartReplica("r1", CART, List.of(other)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aStoredVersionNeedsAWriter() {
    assertThatThrownBy(() -> new CartVersion(CART, VectorClock.empty(), ONE_SEAT, 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("needs a writer");
  }
}
