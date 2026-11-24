package at.dumphey.itemeditor.engine;

import java.util.Locale;

/** "5s", "10t", "2m", "1h", "500ms" or a bare number (in the unit the caller says). */
public final class TimeParser {

    public static final long TICK_MS = 50L;

    private TimeParser() {
    }

    /** @param defaultUnitMs unit of a bare number: 1000 = seconds, 50 = ticks */
    public static long parseMillis(Object value, long defaultUnitMs, ParseContext pc) throws ConfigException {
        if (value instanceof Number) {
            return check((long) (((Number) value).doubleValue() * defaultUnitMs), value, pc);
        }
        final String s = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            throw pc.error("empty duration");
        }
        long unit = defaultUnitMs;
        String number = s;
        if (s.endsWith("ms")) {
            unit = 1L;
            number = s.substring(0, s.length() - 2);
        } else if (s.endsWith("t")) {
            unit = TICK_MS;
            number = s.substring(0, s.length() - 1);
        } else if (s.endsWith("s")) {
            unit = 1000L;
            number = s.substring(0, s.length() - 1);
        } else if (s.endsWith("m")) {
            unit = 60_000L;
            number = s.substring(0, s.length() - 1);
        } else if (s.endsWith("h")) {
            unit = 3_600_000L;
            number = s.substring(0, s.length() - 1);
        }
        try {
            return check((long) (Double.parseDouble(number.trim()) * unit), value, pc);
        } catch (NumberFormatException e) {
            throw pc.error("'" + value + "' is not a duration (examples: 5s, 20t, 2m, 500ms)");
        }
    }

    public static int parseTicks(Object value, long defaultUnitMs, ParseContext pc) throws ConfigException {
        final long ms = parseMillis(value, defaultUnitMs, pc);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, Math.round(ms / (double) TICK_MS)));
    }

    private static long check(long ms, Object original, ParseContext pc) throws ConfigException {
        if (ms < 0) {
            throw pc.error("'" + original + "' cannot be negative");
        }
        return ms;
    }
}
