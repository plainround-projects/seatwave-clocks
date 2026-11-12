package io.plainround.seatwave.clocks;

import java.util.Objects;

/**
 * One version of a cart key, as a replica stores it: its vector clock, its contents, and the
 * wall-clock stamp of the replica that wrote it. Only the SIMPLE design (last writer wins by
 * timestamp) reads the stamp; the PRODUCTION design orders versions by their clocks alone.
 *
 * @param key the cart key, for example {@code cart/0042}
 * @param clock which writes this version has seen
 * @param contents the seats, with their add tags and removal markers
 * @param wallClockMillis the writing replica's wall clock when it wrote this version
 */
public record CartVersion(
    String key, VectorClock clock, CartContents contents, long wallClockMillis) {

  /** Rejects missing parts and a version that no replica wrote (an empty clock). */
  public CartVersion {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(contents, "contents");
    if (clock.size() == 0) {
      throw new IllegalArgumentException("a stored version of " + key + " needs a writer");
    }
  }

  @Override
  public String toString() {
    return clock + " " + contents.seats() + " stamped " + wallClockMillis;
  }
}
