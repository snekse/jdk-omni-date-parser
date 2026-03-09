# jdk-omni-date-parser
A lenient parser that can interpret almost any input and convert it to a ZonedDateTime (defaulting to UTC)


## Performance

Benchmarked with [JMH](https://github.com/openjdk/jmh) on JDK 21 (OpenJDK 64-Bit Server VM, 1 fork, 3 warmup + 5 measurement iterations, throughput mode).

Three strategies measured over 19 representative inputs covering ISO 8601, RFC 2822, Western slash/dash, spelled-out months, AM/PM, and compact numeric formats:

| Strategy | Throughput | vs. Shotgun |
|---|---|---|
| **OmniDateParser** (lexer + state machine) | ~1,089,000 ops/s | **~25x faster** |
| Shotgun (sequential `DateTimeFormatter` tries) | ~43,000 ops/s | baseline |
| Single known formatter (ceiling — one format only) | ~1,084,000 ops/s | ~25x faster |

The shotgun approach — the pattern most developers reach for when handling multiple formats — pays a steep cost in exception creation on every miss. OmniDateParser's single-pass lexer avoids this entirely and matches the throughput of a hand-picked formatter that knows the format in advance.

To reproduce: `./gradlew jmh`

# Format Sources
https://help.qlik.com/talend/en-US/data-preparation-user-guide/8.0/list-of-date-and-date-time-formats 

https://en.wikipedia.org/wiki/List_of_date_formats_by_country

https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html

https://docs.oracle.com/middleware/1221/wcs/tag-ref/MISC/TimeZones.html

https://docs.oracle.com/en/industries/financial-services/financial-services-cloud/communication/time-zone-ids.html

http://www.unicode.org/reports/tr35/tr35-dates.html#Date_Format_Patterns
