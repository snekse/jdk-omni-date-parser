package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that a single shared OmniDateParser instance produces correct results
 * when called from multiple threads simultaneously.
 */
class ConcurrencyTest {

    @Test
    void sharedInstanceIsThreadSafe() throws Exception {
        OmniDateParser parser = new OmniDateParser(OmniDateParserConfig.defaults());
        int threadCount = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        List<Future<ZonedDateTime>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() ->
                    parser.parseZonedDateTime("1999-01-01T00:00:00Z")));
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        ZonedDateTime expected = ZonedDateTime.of(1999, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        for (Future<ZonedDateTime> f : futures) {
            assertEquals(expected, f.get());
        }
    }
}
