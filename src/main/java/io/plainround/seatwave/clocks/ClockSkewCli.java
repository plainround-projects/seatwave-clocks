package io.plainround.seatwave.clocks;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Physical time failing, in four parts: drift turned into offset, the offset estimate of one
 * NTP-style exchange, last-writer-wins by wall-clock stamp across two skewed replicas, and a clock
 * stepped backwards. Deterministic: every clock is scripted, so the output never depends on this
 * machine's own clock.
 */
public final class ClockSkewCli {

  private static final long[] DRIFT_PARTS_PER_MILLION = {10, 50, 100, 200};
  private static final double MILLIS_PER_HOUR = 3_600_000;
  private static final double SECONDS_PER_DAY = 86_400;
  private static final double ONE_MILLION = 1_000_000;
  private static final long OPPOSITE_DRIFT_PPM = 50;
  private static final long[] OFFSET_THRESHOLDS_MILLIS = {100, 200};

  private static final long SERVER_AHEAD_MILLIS = 200;
  private static final long CLIENT_SEND_MILLIS = 1_000;
  private static final long SERVER_PROCESSING_MILLIS = 1;

  private static final long SEED = 25;
  private static final int WRITE_PAIRS = 1_000;
  private static final long REPLICATION_DELAY_MILLIS = 50;
  private static final long LONGEST_GAP_MILLIS = 400;
  private static final long[] SKEWS_MILLIS = {0, 20, 100, 200, 500};

  private static final Instant SALE_OPENS = Instant.parse("2026-11-20T19:00:00Z");
  private static final long STEP_BACK_MILLIS = 300;
  private static final long BOOKING_GAP_MILLIS = 200;

  private ClockSkewCli() {}

  /** Prints the four parts. */
  public static void main(String[] arguments) {
    printDrift();
    printOffsetExchange();
    printLastWriterWins();
    printClockStep();
  }

  private static void printDrift() {
    System.out.println("PART 1. Drift becomes offset (a free-running clock, no synchronization)");
    System.out.println("  drift ppm   ms gained or lost per hour   seconds per day");
    for (long driftPartsPerMillion : DRIFT_PARTS_PER_MILLION) {
      double driftShare = driftPartsPerMillion / ONE_MILLION;
      System.out.printf(
          Locale.ROOT,
          "  %9d   %26.1f   %15.2f%n",
          driftPartsPerMillion,
          driftShare * MILLIS_PER_HOUR,
          driftShare * SECONDS_PER_DAY);
    }
    // Two clocks drifting in opposite directions move apart at the sum of their drifts.
    long relativePartsPerMillion = 2 * OPPOSITE_DRIFT_PPM;
    for (long thresholdMillis : OFFSET_THRESHOLDS_MILLIS) {
      double secondsToReach = thresholdMillis / 1_000.0 / (relativePartsPerMillion / ONE_MILLION);
      System.out.printf(
          Locale.ROOT,
          "  two clocks at +%d and -%d ppm are %d ms apart after %.0f s (%.1f min)%n",
          OPPOSITE_DRIFT_PPM,
          OPPOSITE_DRIFT_PPM,
          thresholdMillis,
          secondsToReach,
          secondsToReach / 60);
    }
  }

  private static void printOffsetExchange() {
    System.out.printf(
        Locale.ROOT,
        "%nPART 2. One NTP-style exchange; the server's clock is %d ms ahead of the client's%n",
        SERVER_AHEAD_MILLIS);
    System.out.println(
        "  delay out / back   t1 (client)   t2 (server)   t3 (server)   t4 (client)"
            + "   offset estimate   round trip   error");
    for (long[] delays : List.of(new long[] {20, 20}, new long[] {5, 35}, new long[] {35, 5})) {
      long outMillis = delays[0];
      long backMillis = delays[1];
      long clientSendMillis = CLIENT_SEND_MILLIS;
      long serverReceiveMillis = clientSendMillis + outMillis + SERVER_AHEAD_MILLIS;
      long serverReplyMillis = serverReceiveMillis + SERVER_PROCESSING_MILLIS;
      long clientReceiveMillis = serverReplyMillis - SERVER_AHEAD_MILLIS + backMillis;
      // The NTP estimate assumes the two directions took equally long; asymmetry is its error.
      double offset =
          ((serverReceiveMillis - clientSendMillis) + (serverReplyMillis - clientReceiveMillis))
              / 2.0;
      long roundTrip =
          (clientReceiveMillis - clientSendMillis) - (serverReplyMillis - serverReceiveMillis);
      System.out.printf(
          Locale.ROOT,
          "  %5d / %-5d   %11d   %11d   %11d   %11d   %12.1f ms   %7d ms   %+5.1f ms%n",
          outMillis,
          backMillis,
          clientSendMillis,
          serverReceiveMillis,
          serverReplyMillis,
          clientReceiveMillis,
          offset,
          roundTrip,
          offset - SERVER_AHEAD_MILLIS);
    }
    System.out.println("  the error is never larger than half the round trip");
  }

  private static void printLastWriterWins() {
    System.out.printf(
        Locale.ROOT,
        "%nPART 3. Last writer wins by wall-clock stamp, %d write pairs on cart keys:%n"
            + "  the phone writes through r1; the laptop, after seeing it (replication %d ms),"
            + " writes through r2%n"
            + "  a gap later, spread evenly over %d to %d ms; r2's clock is behind r1's by the"
            + " skew%n",
        WRITE_PAIRS,
        REPLICATION_DELAY_MILLIS,
        REPLICATION_DELAY_MILLIS,
        LONGEST_GAP_MILLIS);
    System.out.println("  skew ms   later writes dropped   share   largest gap dropped ms");
    for (long skewMillis : SKEWS_MILLIS) {
      // The same seed for every skew, so every row sees the same pairs of writes.
      RandomGenerator random = RandomGeneratorFactory.of("L64X128MixRandom").create(SEED);
      int dropped = 0;
      long largestGapDropped = 0;
      for (int pairIndex = 0; pairIndex < WRITE_PAIRS; pairIndex++) {
        long gapMillis = random.nextLong(REPLICATION_DELAY_MILLIS, LONGEST_GAP_MILLIS + 1);
        long phoneStamp = 0;
        long laptopStamp = gapMillis - skewMillis;
        // The replica keeps the larger stamp; a tie is broken against the later write too.
        if (laptopStamp <= phoneStamp) {
          dropped++;
          largestGapDropped = Math.max(largestGapDropped, gapMillis);
        }
      }
      System.out.printf(
          Locale.ROOT,
          "  %7d   %20d   %4.1f%%   %22d%n",
          skewMillis,
          dropped,
          100.0 * dropped / WRITE_PAIRS,
          largestGapDropped);
    }
  }

  private static void printClockStep() {
    System.out.printf(
        Locale.ROOT,
        "%nPART 4. A wall clock stepped back %d ms between two bookings (java.time.Clock)%n",
        STEP_BACK_MILLIS);
    // Each reading is a Clock, as service code would receive one; offset() models the step.
    Clock beforeStep = Clock.fixed(SALE_OPENS, ZoneOffset.UTC);
    Clock afterStep =
        Clock.offset(
            Clock.fixed(SALE_OPENS.plusMillis(BOOKING_GAP_MILLIS), ZoneOffset.UTC),
            Duration.ofMillis(-STEP_BACK_MILLIS));
    Instant firstStamp = beforeStep.instant();
    Instant secondStamp = afterStep.instant();
    long monotonicFirstNanos = 0;
    long monotonicSecondNanos = Duration.ofMillis(BOOKING_GAP_MILLIS).toNanos();
    System.out.printf(Locale.ROOT, "  booking 1 stamped %s%n", firstStamp);
    System.out.printf(
        Locale.ROOT,
        "  booking 2 stamped %s (made %d ms later)%n",
        secondStamp,
        BOOKING_GAP_MILLIS);
    System.out.printf(
        Locale.ROOT,
        "  order by stamp: booking %s first; elapsed by wall clock %d ms; by monotonic clock %d"
            + " ms%n",
        secondStamp.isBefore(firstStamp) ? "2" : "1",
        Duration.between(firstStamp, secondStamp).toMillis(),
        Duration.ofNanos(monotonicSecondNanos - monotonicFirstNanos).toMillis());
  }
}
