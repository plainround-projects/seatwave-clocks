package io.plainround.seatwave.clocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Pins the two Lamport rules (tick, and receive past both counters) and the total order. */
class LamportClockTest {

  @Test
  void aTickIsOneMoreThanBefore() {
    assertThat(LamportClock.start("r1").tick().tick()).isEqualTo(new LamportClock("r1", 2));
  }

  @Test
  void aReceiveJumpsPastALargerReceivedCounter() {
    LamportClock slowNode = new LamportClock("r2", 3);

    assertThat(slowNode.receive(40).counter()).isEqualTo(41);
  }

  @Test
  void aReceiveKeepsGoingFromALargerLocalCounter() {
    LamportClock busyNode = new LamportClock("r2", 40);

    assertThat(busyNode.receive(3).counter()).isEqualTo(41);
  }

  @Test
  void aReceiveIsOrderedAfterTheSendThatCausedIt() {
    LamportClock sender = new LamportClock("r1", 7).tick();
    LamportClock receiver = new LamportClock("r2", 2).receive(sender.counter());

    assertThat(LamportClock.TOTAL_ORDER.compare(sender, receiver)).isNegative();
  }

  @Test
  void theTotalOrderBreaksTiesByNodeId() {
    List<LamportClock> sorted =
        List.of(new LamportClock("r2", 5), new LamportClock("r1", 5), new LamportClock("r3", 4))
            .stream()
            .sorted(LamportClock.TOTAL_ORDER)
            .toList();

    assertThat(sorted)
        .containsExactly(
            new LamportClock("r3", 4), new LamportClock("r1", 5), new LamportClock("r2", 5));
  }

  @Test
  void rejectsABlankNodeIdAndANegativeCounter() {
    assertThatThrownBy(() -> new LamportClock(" ", 1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LamportClock("r1", -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("r1=-1");
  }
}
