package io.plainround.seatwave.clocks;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The seats in one fan's cart, kept so that two concurrent versions can always be merged: an
 * observed-remove set. Every add of a seat carries a unique tag; a removal records the tags it saw
 * as removal markers. A seat is in the cart while it has at least one tag without a marker, so a
 * removal cancels only the adds it observed, and a concurrent add of the same seat survives.
 * Immutable and therefore thread-safe.
 *
 * @param addTags for each seat label, the tags of every add of it ever seen
 * @param removalMarkers the tags of adds that a removal has observed and cancelled
 */
public record CartContents(Map<String, Set<String>> addTags, Set<String> removalMarkers) {

  /** Seat labels as Module 13 writes them: a row of one or two letters, a dash, a number. */
  private static final Pattern SEAT_LABEL = Pattern.compile("[A-Z]{1,2}-[0-9]{1,3}");

  /** Rejects malformed seat labels and blank tags, and makes deep unmodifiable sorted copies. */
  public CartContents {
    Objects.requireNonNull(addTags, "addTags");
    Objects.requireNonNull(removalMarkers, "removalMarkers");
    TreeMap<String, Set<String>> copiedAdds = new TreeMap<>();
    addTags.forEach((seatLabel, tags) -> {
      requireSeatLabel(seatLabel);
      tags.forEach(CartContents::requireTag);
      copiedAdds.put(seatLabel, Collections.unmodifiableSortedSet(new TreeSet<>(tags)));
    });
    removalMarkers.forEach(CartContents::requireTag);
    addTags = Collections.unmodifiableSortedMap(copiedAdds);
    removalMarkers = Collections.unmodifiableSortedSet(new TreeSet<>(removalMarkers));
  }

  /** An empty cart. */
  public static CartContents empty() {
    return new CartContents(Map.of(), Set.of());
  }

  /**
   * The cart after adding {@code seatLabel} with a tag nobody else uses, such as {@code phone-1}:
   * the device's name and its own count of adds, which no other device can produce.
   */
  public CartContents add(String seatLabel, String uniqueTag) {
    requireSeatLabel(seatLabel);
    requireTag(uniqueTag);
    TreeMap<String, Set<String>> nextAdds = new TreeMap<>(addTags);
    TreeSet<String> tags = new TreeSet<>(addTags.getOrDefault(seatLabel, Set.of()));
    tags.add(uniqueTag);
    nextAdds.put(seatLabel, tags);
    return new CartContents(nextAdds, removalMarkers);
  }

  /**
   * The cart after removing {@code seatLabel}: every add of it that this version has seen gets a
   * removal marker. Adds this version has not seen (made concurrently elsewhere) are untouched.
   */
  public CartContents remove(String seatLabel) {
    requireSeatLabel(seatLabel);
    TreeSet<String> nextMarkers = new TreeSet<>(removalMarkers);
    nextMarkers.addAll(addTags.getOrDefault(seatLabel, Set.of()));
    return new CartContents(addTags, nextMarkers);
  }

  /**
   * Both carts' knowledge in one: the union of add tags and the union of markers. A union is
   * commutative, associative and idempotent, so replicas that merge the same versions agree.
   */
  public CartContents merge(CartContents other) {
    TreeMap<String, Set<String>> nextAdds = new TreeMap<>(addTags);
    other.addTags.forEach((seatLabel, tags) -> {
      TreeSet<String> union = new TreeSet<>(nextAdds.getOrDefault(seatLabel, Set.of()));
      union.addAll(tags);
      nextAdds.put(seatLabel, union);
    });
    TreeSet<String> nextMarkers = new TreeSet<>(removalMarkers);
    nextMarkers.addAll(other.removalMarkers);
    return new CartContents(nextAdds, nextMarkers);
  }

  /** The seats the fan sees: those with at least one add that no removal has cancelled. */
  public SortedSet<String> seats() {
    TreeSet<String> visible = new TreeSet<>();
    addTags.forEach((seatLabel, tags) -> {
      if (!removalMarkers.containsAll(tags)) {
        visible.add(seatLabel);
      }
    });
    return Collections.unmodifiableSortedSet(visible);
  }

  private static void requireSeatLabel(String seatLabel) {
    Objects.requireNonNull(seatLabel, "seat label");
    if (!SEAT_LABEL.matcher(seatLabel).matches()) {
      throw new IllegalArgumentException("not a seat label such as A-7: " + seatLabel);
    }
  }

  private static void requireTag(String tag) {
    Objects.requireNonNull(tag, "tag");
    if (tag.isBlank()) {
      throw new IllegalArgumentException("an add tag must not be blank");
    }
  }
}
