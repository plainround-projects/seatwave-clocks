package io.plainround.seatwave.clocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pins CartContents validation and the add, remove and merge rules of the observed-remove set. */
class CartContentsTest {

  @ParameterizedTest
  @ValueSource(strings = {"", "A7", "a-7", "ABC-7", "A-", "A-1000", "A-7 ", "7-A"})
  void addRejectsAMalformedSeatLabel(String label) {
    assertThatThrownBy(() -> CartContents.empty().add(label, "phone-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a seat label");
  }

  @Test
  void addRejectsABlankTag() {
    assertThatThrownBy(() -> CartContents.empty().add("A-7", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
  }

  @Test
  void removeRejectsAMalformedSeatLabel() {
    assertThatThrownBy(() -> CartContents.empty().remove("seven"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theConstructorRejectsMalformedLabelsBlankTagsAndBlankMarkers() {
    assertThatThrownBy(() -> new CartContents(Map.of("x", Set.of("t")), Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CartContents(Map.of("A-7", Set.of("")), Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CartContents(Map.of(), Set.of(" ")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aSeatIsVisibleWhileOneAddHasNoMarker() {
    CartContents twoAdds = CartContents.empty().add("A-7", "phone-1").add("A-7", "laptop-1");
    CartContents oneMarker = new CartContents(twoAdds.addTags(), Set.of("phone-1"));

    assertThat(oneMarker.seats()).containsExactly("A-7");
    assertThat(twoAdds.remove("A-7").seats()).isEmpty();
  }

  @Test
  void removalLeavesAnAddItHasNotSeen() {
    CartContents seen = CartContents.empty().add("A-7", "phone-1");
    CartContents concurrentAdd = CartContents.empty().add("A-7", "laptop-1");

    assertThat(seen.remove("A-7").merge(concurrentAdd).seats()).containsExactly("A-7");
  }

  @Test
  void aMarkerStaysAfterAMergeSoARemovedSeatDoesNotComeBack() {
    CartContents added = CartContents.empty().add("A-8", "phone-1");
    CartContents removed = added.remove("A-8");

    assertThat(removed.removalMarkers()).containsExactly("phone-1");
    assertThat(added.merge(removed).seats()).isEmpty();
    assertThat(removed.merge(added).seats()).isEmpty();
  }

  @Test
  void mergeIsIdempotentAndCommutative() {
    CartContents left = CartContents.empty().add("A-7", "phone-1").remove("A-7");
    CartContents right = CartContents.empty().add("B-2", "laptop-1");

    assertThat(left.merge(left)).isEqualTo(left);
    assertThat(left.merge(right)).isEqualTo(right.merge(left));
  }

  @Test
  void theCopiesCannotBeChangedFromOutside() {
    CartContents cart = CartContents.empty().add("A-7", "phone-1");

    assertThatThrownBy(() -> cart.addTags().put("A-8", Set.of("x")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> cart.removalMarkers().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
