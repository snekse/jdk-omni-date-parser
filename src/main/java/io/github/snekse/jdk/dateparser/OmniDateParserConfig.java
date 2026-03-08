package io.github.snekse.jdk.dateparser;

import lombok.Builder;
import lombok.Value;

import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Immutable configuration for {@link OmniDateParser}.
 *
 * <p>Use {@link #defaults()} for zero-config parsing (UTC zone, MDY order, pivot year 70),
 * or build a custom instance via {@code builder()}:
 *
 * <pre>{@code
 * OmniDateParserConfig config = OmniDateParserConfig.builder()
 *     .dateOrder(DateOrder.DMY)
 *     .defaultZone(ZoneId.of("Europe/London"))
 *     .pivotYear(50)
 *     .build();
 * OmniDateParser parser = new OmniDateParser(config);
 * }</pre>
 */
@Value
@Builder
public class OmniDateParserConfig {

    /**
     * Controls component ordering for ambiguous numeric inputs (e.g. "10/11/12").
     * Only applied when the parser cannot determine order from heuristics alone.
     * Default: MDY (US convention).
     */
    @Builder.Default DateOrder dateOrder = DateOrder.MDY;

    /**
     * Timezone applied when the input contains no timezone information.
     * Accepts any ZoneId:
     *   ZoneOffset.UTC               → always UTC (default)
     *   ZoneId.systemDefault()       → JVM system timezone
     *   ZoneId.of("Asia/Kolkata")    → specific zone, e.g. India (UTC+5:30)
     *   ZoneId.of("Asia/Kathmandu")  → specific zone, e.g. Nepal (UTC+5:45)
     * Note: half-hour and 45-minute offsets are fully supported via ZoneId.
     */
    @Builder.Default ZoneId defaultZone = ZoneOffset.UTC;

    /**
     * Two-digit year cutoff: years <= pivotYear map to 20xx, years > pivotYear map to 19xx.
     * Example with default 70: "99" -> 1999, "70" -> 2070, "69" -> 2069.
     */
    @Builder.Default int pivotYear = 70;

    /**
     * Returns a config with all defaults: MDY order, UTC zone, pivot year 70.
     *
     * @return the default configuration
     */
    public static OmniDateParserConfig defaults() {
        return builder().build();
    }
}
