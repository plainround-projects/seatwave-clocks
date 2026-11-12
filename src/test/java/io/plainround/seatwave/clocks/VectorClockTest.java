package io.plainround.seatwave.clocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pins the four answers of a vector-clock comparison and the merge they rest on. */
class VectorClockTest {

  private static final VectorClock FIRST = VectorClock.empty().increment("r1");
  private static final VectorClock PHONE = FIRST.increment("r1");
  private static final VectorClock LAPTOP = FIRST.increment("r2");

  @Test
  void ordersAVersionBeforeOneThatWasWrittenOnTopOfIt() {
    assertThat(FIRST.compareTo(PHONE)).isEqualTo(VectorClock.Ordering.BEFORE);
    assertThat(PHONE.compareTo(FIRST)).isEqualTo(VectorClock.Ordering.AFTER);
  }

  @Test
  void reportsConcurrentWhenEachHasACounterTheOtherLacks() {
    assertThat(PHONE.compareTo(LAPTOP)).isEqualTo(VectorClock.Ordering.CONCURRENT);
    assertThat(LAPTOP.compareTo(PHONE)).isEqualTo(VectorClock.Ordering.CONCURRENT);
  }

  @Test
  void reportsEqualForTheSameWritesWhateverTheInsertionOrder() {
    VectorClock built = new VectorClock(Map.of("r2", 1L, "r1", 1L));

    assertThat(built.compareTo(LAPTOP)).isEqualTo(VectorClock.Ordering.EQUAL);
    assertThat(built).isEqualTo(LAPTOP);
  }

  @Test
  void mergeIsTheSmallestClockAfterBothAndIgnoresOrderAndRepeats() {
    VectorClock merged = PHONE.merge(LAPTOP);

    assertThat(merged).isEqualTo(new VectorClock(Map.of("r1", 2L, "r2", 1L)));
    assertThat(LAPTOP.merge(PHONE)).isEqualTo(merged);
    assertThat(merged.merge(PHONE)).isEqualTo(merged);
    assertThat(merged.dominates(PHONE) && merged.dominates(LAPTOP)).isTrue();
  }

  @Test
  void rejectsACounterBelowOne() {
    assertThatThrownBy(() -> new VectorClock(Map.of("r1", 0L)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("r1=0");
  }
}
