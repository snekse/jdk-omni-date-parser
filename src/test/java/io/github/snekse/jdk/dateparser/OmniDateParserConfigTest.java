package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OmniDateParserConfigTest {

    @Test
    void defaults_returnsExpectedDefaultValues() {
        OmniDateParserConfig config = OmniDateParserConfig.defaults();

        assertEquals(DateOrder.MDY, config.getDateOrder());
        assertEquals(ZoneOffset.UTC, config.getDefaultZone());
        assertEquals(70, config.getPivotYear());
    }

    @Test
    void builder_canCreateCustomConfiguration() {
        ZoneId customZone = ZoneId.of("Europe/Paris");
        OmniDateParserConfig config = OmniDateParserConfig.builder()
                .dateOrder(DateOrder.DMY)
                .defaultZone(customZone)
                .pivotYear(50)
                .build();

        assertEquals(DateOrder.DMY, config.getDateOrder());
        assertEquals(customZone, config.getDefaultZone());
        assertEquals(50, config.getPivotYear());
    }
}
