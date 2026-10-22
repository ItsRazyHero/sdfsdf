package io.github.itsrazyhero.menuforge.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses "1d2h30m15s", "90s", "20" (seconds) into milliseconds and formats the remainder. */
public final class Durations {

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*([dhmst]?)");

    private Durations() {}

    public static long parseMillis(String input) {
        if (input == null || input.isBlank()) return 0;
        String s = input.trim().toLowerCase();
        Matcher m = PART.matcher(s);
        long total = 0;
        boolean any = false;
        while (m.find()) {
            any = true;
            long n = Long.parseLong(m.group(1));
            total += switch (m.group(2)) {
                case "d" -> n * 86_400_000L;
                case "h" -> n * 3_600_000L;
                case "m" -> n * 60_000L;
                case "t" -> n * 50L;
                default -> n * 1000L;
            };
        }
        if (!any) throw new IllegalArgumentException("Bad duration: " + input);
        return total;
    }

    /** Formats millis with a pattern like "{d}d {h}h {m}m {s}s"; zero leading units are dropped. */
    public static String format(long millis, String pattern) {
        long secs = Math.max(0, (millis + 999) / 1000);
        long d = secs / 86_400, h = (secs % 86_400) / 3600, m = (secs % 3600) / 60, s = secs % 60;
        String out = pattern;
        // drop leading units that are zero so "1d 0h 0m 5s" becomes "5s"
        if (d == 0) out = out.replaceAll("\\{d}[^{]*", "");
        if (d == 0 && h == 0) out = out.replaceAll("\\{h}[^{]*", "");
        if (d == 0 && h == 0 && m == 0) out = out.replaceAll("\\{m}[^{]*", "");
        return out.replace("{d}", String.valueOf(d)).replace("{h}", String.valueOf(h))
                .replace("{m}", String.valueOf(m)).replace("{s}", String.valueOf(s)).trim();
    }
}
