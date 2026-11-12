package io.plainround.seatwave.clocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pins the counters' validation, their value and their merge rule. */
class CounterValidationTest {

  @Test
  void anIncrementMustBePositive() {
    assertThatThrownBy(() -> GrowOnlyCounter.zero().increment("r1", 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("only grows");
    assertThatThrownBy(() -> GrowOnlyCounter.zero().increment("r1", -5))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aTallyBelowOneOrABlankReplicaIdIsRejected() {
    assertThatThrownBy(() -> new GrowOnlyCounter(Map.of("r1", 0L)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("r1=0");
    assertThatThrownBy(() -> new GrowOnlyCounter(Map.of(" ", 1L)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theValueIsTheSumOfTheTalliesAndMergeTakesTheLargerPerReplica() {
    GrowOnlyCounter left = GrowOnlyCounter.zero().increment("r1", 5).increment("r2", 2);
    GrowOnlyCounter right = GrowOnlyCounter.zero().increment("r1", 3).increment("r3", 4);

    assertThat(left.value()).isEqualTo(7);
    assertThat(left.merge(right).value()).isEqualTo(5 + 2 + 4);
    assertThat(left.merge(left)).isEqualTo(left);
  }

  @Test
  void aSumOverflowIsAnErrorNotAWrongNumber() {
    GrowOnlyCounter nearTheLimit = GrowOnlyCounter.zero().increment("r1", Long.MAX_VALUE);

    assertThatThrownBy(() -> nearTheLimit.increment("r1", 1))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> nearTheLimit.increment("r2", 1).value())
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void aPositiveNegativeCounterRejectsMissingHalvesAndNonPositiveAmounts() {
    assertThatThrownBy(() -> new PositiveNegativeCounter(null, GrowOnlyCounter.zero()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> PositiveNegativeCounter.zero().decrement("r1", 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PositiveNegativeCounter.zero().increment("r1", -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aPositiveNegativeCounterCanGoBelowZeroAndMergesEachHalfByMax() {
    PositiveNegativeCounter seatsLeft = PositiveNegativeCounter.zero().increment("r1", 1);
    PositiveNegativeCounter onR1 = seatsLeft.decrement("r1", 1);
    PositiveNegativeCounter onR2 = seatsLeft.decrement("r2", 1);

    assertThat(onR1.merge(onR2).value()).isEqualTo(-1);
    assertThat(onR1.merge(onR2).merge(onR1).value()).isEqualTo(-1);
  }
}
