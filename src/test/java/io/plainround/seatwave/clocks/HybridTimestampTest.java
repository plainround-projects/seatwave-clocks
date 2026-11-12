package io.plainround.seatwave.clocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Pins the hybrid clock's rules: wall clock when ahead, counter otherwise, never backward. */
class HybridTimestampTest {

  @Test
  void aTickTakesTheWallClockWhenItIsAheadAndResetsTheCounter() {
    HybridTimestamp tick = new HybridTimestamp(1_000, 5).tick(1_007);

    assertThat(tick).isEqualTo(new HybridTimestamp(1_007, 0));
  }

  @Test
  void aTickInTheSameMillisecondCountsUp() {
    assertThat(new HybridTimestamp(1_000, 5).tick(1_000)).isEqualTo(new HybridTimestamp(1_000, 6));
  }

  @Test
  void aTickKeepsGoingForwardWhenTheWallClockStepsBackward() {
    HybridTimestamp before = new HybridTimestamp(1_000, 0);
    HybridTimestamp afterStep = before.tick(800);

    assertThat(afterStep).isEqualTo(new HybridTimestamp(1_000, 1));
    assertThat(afterStep).isGreaterThan(before);
  }

  @Test
  void aReceiveWhenTheWallClockIsAheadOfBothTakesTheWallClock() {
    HybridTimestamp mine = new HybridTimestamp(1_000, 3);

    assertThat(mine.receive(new HybridTimestamp(1_010, 4), 1_500))
        .isEqualTo(new HybridTimestamp(1_500, 0));
  }

  @Test
  void aReceiveFromTheFutureContinuesTheSendersCounter() {
    HybridTimestamp mine = new HybridTimestamp(1_000, 3);

    assertThat(mine.receive(new HybridTimestamp(1_200, 4), 1_050))
        .isEqualTo(new HybridTimestamp(1_200, 5));
  }

  @Test
  void aReceiveWhenMyTimestampIsTheLargestContinuesMyCounter() {
    HybridTimestamp mine = new HybridTimestamp(1_200, 3);

    assertThat(mine.receive(new HybridTimestamp(1_000, 9), 1_050))
        .isEqualTo(new HybridTimestamp(1_200, 4));
  }

  @Test
  void aReceiveWithTheSamePhysicalPartTakesTheLargerCounterPlusOne() {
    HybridTimestamp mine = new HybridTimestamp(1_200, 3);

    assertThat(mine.receive(new HybridTimestamp(1_200, 9), 1_100))
        .isEqualTo(new HybridTimestamp(1_200, 10));
  }

  @Test
  void aReceiveIsAfterBothTheLocalAndTheReceivedTimestampEvenWithABackwardWallClock() {
    HybridTimestamp mine = new HybridTimestamp(1_000, 3);
    HybridTimestamp theirs = new HybridTimestamp(1_000, 8);

    HybridTimestamp result = mine.receive(theirs, 700);

    assertThat(result).isGreaterThan(mine).isGreaterThan(theirs);
  }

  @Test
  void thePhysicalPartNeverRunsAheadOfTheLargestWallClockAnyNodeRead() {
    long sendersFastWallClock = 1_200;
    HybridTimestamp sent = new HybridTimestamp(0, 0).tick(sendersFastWallClock);
    long receiversSlowWallClock = 1_000;

    HybridTimestamp received =
        new HybridTimestamp(0, 0)
            .receive(sent, receiversSlowWallClock)
            .tick(receiversSlowWallClock);

    assertThat(received.physicalMillis()).isEqualTo(sendersFastWallClock);
    assertThat(received.physicalMillis())
        .isLessThanOrEqualTo(Math.max(sendersFastWallClock, receiversSlowWallClock));
  }

  @Test
  void ordersByPhysicalPartThenByCounter() {
    assertThat(new HybridTimestamp(5, 9)).isLessThan(new HybridTimestamp(6, 0));
    assertThat(new HybridTimestamp(5, 1)).isLessThan(new HybridTimestamp(5, 2));
  }

  @Test
  void rejectsNegativeParts() {
    assertThatThrownBy(() -> new HybridTimestamp(-1, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("-1.0");
    assertThatThrownBy(() -> new HybridTimestamp(0, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
