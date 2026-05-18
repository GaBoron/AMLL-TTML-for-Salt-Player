package dev.amll.saltplayer.ttml;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 返回给播放器前的歌词清理：去掉来源行、过滤 LRC 元数据并应用时间偏移。
 */
final class LyricPostProcessor {
    private static final Pattern SOURCE_LINE = Pattern.compile("^\\[[0-9]{1,3}:[0-9]{2}(?:\\.[0-9]{1,3})?]\\u6765\\u6e90\\uff1a[^\\r\\n]*(?:\\R|$)");
    private static final Pattern METADATA_LINE = Pattern.compile("^\\s*\\[[A-Za-z][A-Za-z0-9_ -]*:[^]]*]\\s*$");
    private static final Pattern TIMESTAMP = Pattern.compile("([\\[<])([0-9]{1,3}:[0-9]{2}(?::[0-9]{2})?(?:\\.[0-9]{1,3})?)([])>])");

    private LyricPostProcessor() {
    }

    static String prepareForDisplay(String lyrics, int offsetMillis) {
        String cleaned = removeSourceLine(stripMetadataLines(lyrics == null ? "" : lyrics));
        return offsetMillis == 0 ? cleaned : shiftTimestamps(cleaned, offsetMillis);
    }

    private static String stripMetadataLines(String lyrics) {
        StringBuilder builder = new StringBuilder();
        for (String line : lyrics.split("\\R", -1)) {
            if (METADATA_LINE.matcher(line).matches()) continue;
            builder.append(line).append(System.lineSeparator());
        }
        return builder.toString().stripTrailing();
    }

    private static String removeSourceLine(String lyrics) {
        return SOURCE_LINE.matcher(lyrics).replaceFirst("");
    }

    private static String shiftTimestamps(String lyrics, int offsetMillis) {
        // 同时处理行首时间戳和逐词内联时间戳。
        Matcher matcher = TIMESTAMP.matcher(lyrics);
        StringBuilder shifted = new StringBuilder();
        while (matcher.find()) {
            Long millis = parseTimestamp(matcher.group(2));
            if (millis == null) continue;

            long adjusted = Math.max(0L, millis + offsetMillis);
            matcher.appendReplacement(shifted, Matcher.quoteReplacement(matcher.group(1) + formatTimestamp(adjusted, matcher.group(3))));
        }
        matcher.appendTail(shifted);
        return shifted.toString();
    }

    private static Long parseTimestamp(String value) {
        String[] parts = value.split(":");
        try {
            if (parts.length == 2) {
                return (long) ((Long.parseLong(parts[0]) * 60 + Double.parseDouble(parts[1])) * 1000);
            }
            if (parts.length == 3) {
                return (long) ((Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60 + Double.parseDouble(parts[2])) * 1000);
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    private static String formatTimestamp(long millis, String closing) {
        long minutes = millis / 60000;
        long seconds = millis % 60000 / 1000;
        long milliseconds = millis % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, milliseconds) + closing;
    }
}
