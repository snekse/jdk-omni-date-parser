package io.github.snekse.jdk.dateparser.bench;

import io.github.snekse.jdk.dateparser.OmniDateParser;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH throughput benchmark comparing three date-parsing strategies:
 *
 * <ol>
 *   <li><b>omniDateParser</b> — our lexer + state machine, round-robins over all formats</li>
 *   <li><b>shotgun</b> — naive sequential formatter tries with exception catching, round-robins</li>
 *   <li><b>singleKnownFormatter</b> — one {@link DateTimeFormatter} for a single known format;
 *       this is the throughput ceiling when the format is known in advance.
 *       <em>Note: not apples-to-apples — always parses the same ISO 8601 input.</em></li>
 * </ol>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class OmniDateParserBenchmark {

    private List<String> inputs;
    private int index;

    /**
     * Single known formatter for ISO 8601 with offset — the theoretical throughput ceiling.
     * Only used by {@link #singleKnownFormatter()}.
     */
    private static final DateTimeFormatter ISO_KNOWN =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");

    @Setup
    public void setup() {
        inputs = BenchmarkInputs.ALL;
        index = 0;
    }

    /** Returns the next input from the round-robin cycle. */
    private String next() {
        String s = inputs.get(index % inputs.size());
        index++;
        return s;
    }

    /**
     * Measures OmniDateParser throughput across all benchmark inputs (round-robin).
     */
    @Benchmark
    public ZonedDateTime omniDateParser() {
        return OmniDateParser.toZonedDateTime(next());
    }

    /**
     * Measures shotgun (sequential formatter tries) throughput across all benchmark inputs.
     */
    @Benchmark
    public ZonedDateTime shotgun() {
        return ShotgunDateParser.parse(next());
    }

    /**
     * Measures the throughput ceiling: a single {@link DateTimeFormatter} called with exactly
     * the right format for a fixed ISO 8601 input.
     *
     * <p><b>Note:</b> this benchmark always parses the same ISO 8601 string — it is NOT
     * apples-to-apples with the other two benchmarks. It shows what is possible when
     * the format is known in advance.
     */
    @Benchmark
    public ZonedDateTime singleKnownFormatter() {
        return ZonedDateTime.parse("1999-01-01T00:00:00Z", ISO_KNOWN);
    }
}
