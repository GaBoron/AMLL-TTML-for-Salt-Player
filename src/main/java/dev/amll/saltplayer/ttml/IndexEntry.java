package dev.amll.saltplayer.ttml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AMLL raw-lyrics-index.jsonl 中的一条索引记录。
 */
final class IndexEntry {
    final Map<String, List<String>> metadata;
    final String rawLyricFile;

    private IndexEntry(Map<String, List<String>> metadata, String rawLyricFile) {
        this.metadata = metadata;
        this.rawLyricFile = rawLyricFile;
    }

    static IndexEntry fromJsonLine(String line) {
        // 索引文件结构固定，这里用轻量解析避免引入额外 JSON 依赖。
        Matcher rawMatcher = Pattern.compile("\"rawLyricFile\"\\s*:\\s*\"([^\"]+)\"").matcher(line);
        String rawLyricFile = rawMatcher.find() ? unescapeJson(rawMatcher.group(1)) : "";

        String metadataBlock = between(line, "\"metadata\":[", "],\"rawLyricFile\"");
        Map<String, List<String>> metadata = new LinkedHashMap<>();

        int index = 0;
        while (index < metadataBlock.length()) {
            int keyStart = metadataBlock.indexOf("[\"", index);
            if (keyStart < 0) break;
            int keyEnd = metadataBlock.indexOf('"', keyStart + 2);
            if (keyEnd < 0) break;
            String key = unescapeJson(metadataBlock.substring(keyStart + 2, keyEnd));

            int valuesStart = metadataBlock.indexOf('[', keyEnd);
            if (valuesStart < 0) break;
            int valuesEnd = findClosingBracket(metadataBlock, valuesStart);
            if (valuesEnd < 0) break;

            metadata.put(key, parseStringArray(metadataBlock.substring(valuesStart + 1, valuesEnd)));
            index = valuesEnd + 1;
        }

        return new IndexEntry(metadata, rawLyricFile);
    }

    private static String between(String input, String start, String end) {
        int startIndex = input.indexOf(start);
        if (startIndex < 0) return "";
        int contentStart = startIndex + start.length();
        int endIndex = input.indexOf(end, contentStart);
        return endIndex < 0 ? "" : input.substring(contentStart, endIndex);
    }

    private static List<String> parseStringArray(String input) {
        List<String> values = new ArrayList<>();
        int index = 0;
        while (index < input.length()) {
            int start = input.indexOf('"', index);
            if (start < 0) break;
            StringBuilder text = new StringBuilder();
            int cursor = start + 1;
            boolean escaped = false;
            while (cursor < input.length()) {
                char ch = input.charAt(cursor);
                if (escaped) {
                    text.append('\\').append(ch);
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    break;
                } else {
                    text.append(ch);
                }
                cursor++;
            }
            values.add(unescapeJson(text.toString()));
            index = cursor + 1;
        }
        return values;
    }

    private static int findClosingBracket(String input, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (ch == '\\' && inString) {
                escaped = true;
            } else if (ch == '"') {
                inString = !inString;
            } else if (!inString && ch == '[') {
                depth++;
            } else if (!inString && ch == ']') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static String unescapeJson(String value) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch != '\\' || i + 1 >= value.length()) {
                output.append(ch);
                continue;
            }
            char escaped = value.charAt(++i);
            switch (escaped) {
                case '"', '\\', '/' -> output.append(escaped);
                case 'b' -> output.append('\b');
                case 'f' -> output.append('\f');
                case 'n' -> output.append('\n');
                case 'r' -> output.append('\r');
                case 't' -> output.append('\t');
                case 'u' -> {
                    String hex = value.substring(i + 1, i + 5);
                    output.append((char) Integer.parseInt(hex, 16));
                    i += 4;
                }
                default -> output.append(escaped);
            }
        }
        return output.toString();
    }
}
