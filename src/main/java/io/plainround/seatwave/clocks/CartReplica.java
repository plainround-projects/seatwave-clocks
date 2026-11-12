package io.plainround.seatwave.clocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One replica's copy of one cart key: the versions it holds that no other version it holds has seen
 * (the siblings). Immutable: {@link #receive} returns the replica's next state, which makes the
 * module's invariant checkable by comparing values. Every version, local or replicated, enters
 * through {@link #receive}, so no code path can store a version without the dominance check.
 *
 * @param replicaId this replica's id, for example {@code r1}; the entry it increments
 * @param key the cart key, for example {@code cart/0042}
 * @param siblings the versions no other held version dominates, sorted by clock
 */
public record CartReplica(String replicaId, String key, List<CartVersion> siblings) {

  /** Sorting by the clock's text makes two replicas holding the same siblings equal values. */
  private static final Comparator<CartVersion> BY_CLOCK =
      Comparator.comparing(version -> version.clock().toString());

  /** Rejects missing parts and siblings of another key, and sorts the siblings. */
  public CartReplica {
    Objects.requireNonNull(replicaId, "replicaId");
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(siblings, "siblings");
    for (CartVersion sibling : siblings) {
      if (!sibling.key().equals(key)) {
        throw new IllegalArgumentException(sibling.key() + " stored under " + key);
      }
    }
    siblings = siblings.stream().sorted(BY_CLOCK).toList();
  }

  /** A replica that holds no version of {@code key} yet. */
  public static CartReplica empty(String replicaId, String key) {
    return new CartReplica(replicaId, key, List.of());
  }

  /**
   * The context a client must send back with its next write: the merge of every sibling's clock,
   * which says "I have seen all of these".
   */
  public VectorClock context() {
    return siblings.stream()
        .map(CartVersion::clock)
        .reduce(VectorClock.empty(), VectorClock::merge);
  }

  /** The cart the fan sees: every sibling's contents merged. */
  public CartContents mergedContents() {
    return siblings.stream()
        .map(CartVersion::contents)
        .reduce(CartContents.empty(), CartContents::merge);
  }

  /**
   * A new version written through this replica by a client that had read {@code context}: the
   * context with this replica's counter plus one. Empty when the context lacks a write this replica
   * itself made: a clock with one counter per replica cannot tell that write apart from the new
   * one, so the new clock would claim to have seen it and silently replace it. The client must read
   * again and merge (failing safe); production code would return a Result with a sealed error, and
   * dotted version vectors (Section 10) remove the refusal.
   */
  public Optional<CartVersion> newVersion(
      VectorClock context, CartContents contents, long wallClockMillis) {
    long highestOwnCounter =
        siblings.stream()
            .mapToLong(sibling -> sibling.clock().counterOf(replicaId))
            .max()
            .orElse(0L);
    if (context.counterOf(replicaId) < highestOwnCounter) {
      return Optional.empty();
    }
    return Optional.of(
        new CartVersion(key, context.increment(replicaId), contents, wallClockMillis));
  }

  /**
   * The replica after storing {@code incoming}, whether written here or replicated: ignored if a
   * held version has already seen it (so a duplicate or late delivery changes nothing), otherwise
   * kept, and every held version it has seen is dropped. Concurrent versions stay side by side.
   */
  public CartReplica receive(CartVersion incoming) {
    Objects.requireNonNull(incoming, "incoming");
    if (!incoming.key().equals(key)) {
      throw new IllegalArgumentException(incoming.key() + " sent to the replica of " + key);
    }
    boolean alreadySeen =
        siblings.stream().anyMatch(held -> held.clock().dominates(incoming.clock()));
    if (alreadySeen) {
      return this;
    }
    List<CartVersion> next = new ArrayList<>();
    for (CartVersion held : siblings) {
      if (!incoming.clock().dominates(held.clock())) {
        next.add(held);
      }
    }
    next.add(incoming);
    return new CartReplica(replicaId, key, next);
  }

  /** Whether two replicas hold the same siblings, whatever their ids: the convergence check. */
  public boolean holdsSameVersionsAs(CartReplica other) {
    return siblings.equals(other.siblings);
  }
}
