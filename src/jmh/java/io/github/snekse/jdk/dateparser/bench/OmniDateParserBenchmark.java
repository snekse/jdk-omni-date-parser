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

    private List<String> allInputs;
    private List<String> coreInputs;
    private int allIndex;
    private int coreIndex;

    /**
     * Single known formatter for ISO 8601 with offset — the theoretical throughput ceiling.
     * Only used by {@link #singleKnownFormatter()}.
     */
    private static final DateTimeFormatter ISO_KNOWN =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");

    @Setup
    public void setup() {
        allInputs = BenchmarkInputs.ALL;
        coreInputs = BenchmarkInputs.CORE;
        allIndex = 0;
        coreIndex = 0;
    }

    private String nextAll() {
        String s = allInputs.get(allIndex % allInputs.size());
        allIndex++;
        return s;
    }

    private String nextCore() {
        String s = coreInputs.get(coreIndex % coreInputs.size());
        coreIndex++;
        return s;
    }

    /**
     * OmniDateParser over all 23 inputs (including ordinal/period formats).
     */
    @Benchmark
    public ZonedDateTime omniDateParser() {
        return OmniDateParser.toZonedDateTime(nextAll());
    }

    /**
     * OmniDateParser over the 21 core inputs (no ordinal/period). Paired with
     * {@link #shotgunCore()} for a fair apples-to-apples comparison.
     */
    @Benchmark
    public ZonedDateTime omniDateParserCore() {
        return OmniDateParser.toZonedDateTime(nextCore());
    }

    /**
     * Shotgun over all 23 inputs — includes ordinal-suffix and period-suffix preprocessing.
     */
    @Benchmark
    public ZonedDateTime shotgun() {
        return ShotgunDateParser.parse(nextAll());
    }

    /**
     * Shotgun over the 21 core inputs — no ordinal/period preprocessing.
     * Paired with {@link #omniDateParserCore()} for a fair apples-to-apples comparison.
     */
    @Benchmark
    public ZonedDateTime shotgunCore() {
        return ShotgunDateParser.parseCore(nextCore());
    }

    /**
     * Measures the throughput ceiling: a single {@link DateTimeFormatter} called with exactly
     * the right format for a fixed ISO 8601 input.
     *
     * <p><b>Note:</b> this benchmark always parses the same ISO 8601 string — it is NOT
     * apples-to-apples with the other benchmarks. It shows what is possible when
     * the format is known in advance.
     */
    @Benchmark
    public ZonedDateTime singleKnownFormatter() {
        return ZonedDateTime.parse("1999-01-01T00:00:00Z", ISO_KNOWN);
    }
}
