package dev.amll.saltplayer.ttml;

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TtmlToSplConverter {
    private TtmlToSplConverter() {
    }

    static String convert(String ttml, PlaybackExtensionPoint.MediaItem mediaItem) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(ttml)));

        List<SplLine> output = new ArrayList<>();
        NodeList pElements = document.getElementsByTagName("p");
        for (int i = 0; i < pElements.getLength(); i++) {
            if (!(pElements.item(i) instanceof Element p)) continue;
            Long begin = attrMillis(p, "begin");
            if (begin == null) continue;
            Long end = attrMillis(p, "end");

            TimedText main = collectMainText(p);
            String translation = firstRoleText(p, "x-translation");
            String roman = firstRoleText(p, "x-roman");
            if (!main.text().isBlank()) {
                output.add(new SplLine(begin, end, main.toSplText(begin, end), translation != null ? translation : roman, 0));
            }
            output.addAll(collectBackgroundLines(p));
        }

        output.sort(Comparator.comparingLong((SplLine line) -> line.start).thenComparingInt(line -> line.priority));

        StringBuilder builder = new StringBuilder();
        builder.append("[00:00.000]\u6765\u6e90\uff1aAMLL").append('\n');

        Map<Long, Integer> occupiedStarts = new HashMap<>();
        for (SplLine line : output) {
            long start = disambiguateStart(line.start, occupiedStarts, line.priority);
            builder.append(formatLine(start, line.text, line.end)).append('\n');
            if (line.subText != null && !line.subText.isBlank()) {
                builder.append(formatTimestamp(start)).append(line.subText).append('\n');
            }
        }
        return builder.toString();
    }

    private static long disambiguateStart(long start, Map<Long, Integer> occupiedStarts, int priority) {
        if (priority == 1) return start;
        int count = occupiedStarts.getOrDefault(start, 0);
        occupiedStarts.put(start, count + 1);
        return start + count;
    }

    private static String formatLine(long start, String text, Long end) {
        StringBuilder builder = new StringBuilder();
        builder.append(formatTimestamp(start)).append(text);
        if (end != null && end > start) builder.append(formatTimestamp(end));
        return builder.toString();
    }

    private static TimedText collectMainText(Element element) {
        List<TimedPart> parts = new ArrayList<>();
        collectMainParts(element, parts);
        return new TimedText(parts);
    }

    private static void collectMainParts(Node node, List<TimedPart> parts) {
        Node child = node.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getNodeValue();
                if (!text.isEmpty()) parts.add(new TimedPart(null, null, text));
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                String role = role(element);
                if (!"x-translation".equals(role) && !"x-roman".equals(role) && !"x-bg".equals(role)) {
                    Long begin = attrMillis(element, "begin");
                    Long end = attrMillis(element, "end");
                    String text = collectPlainTextSkippingRoles(element);
                    if (!text.isBlank() || " ".equals(text)) {
                        parts.add(new TimedPart(begin, end, text));
                    } else {
                        collectMainParts(element, parts);
                    }
                }
            }
            child = child.getNextSibling();
        }
    }

    private static String collectPlainTextSkippingRoles(Node node) {
        StringBuilder text = new StringBuilder();
        Node child = node.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.TEXT_NODE) {
                text.append(child.getNodeValue());
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                String role = role(element);
                if (!"x-translation".equals(role) && !"x-roman".equals(role)) {
                    text.append(collectPlainTextSkippingRoles(element));
                }
            }
            child = child.getNextSibling();
        }
        return text.toString();
    }

    private static String firstRoleText(Element element, String role) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (role.equals(role(childElement))) {
                    String text = collectPlainTextSkippingRoles(childElement).trim();
                    if (!text.isBlank()) return text;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private static List<SplLine> collectBackgroundLines(Element element) {
        List<SplLine> lines = new ArrayList<>();
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if ("x-bg".equals(role(childElement))) {
                    Long begin = attrMillis(childElement, "begin");
                    if (begin == null) begin = attrMillis(element, "begin");
                    Long end = attrMillis(childElement, "end");
                    if (end == null) end = attrMillis(element, "end");

                    TimedText text = collectBackgroundText(childElement, begin, end);
                    String translation = firstRoleText(childElement, "x-translation");
                    if (begin != null && !text.text().isBlank()) {
                        lines.add(new SplLine(begin, end, ensureParenthesized(text.toSplText(begin, end)), translation, 1));
                    }
                }
            }
            child = child.getNextSibling();
        }
        return lines;
    }

    private static TimedText collectBackgroundText(Element element, Long defaultBegin, Long defaultEnd) {
        List<TimedPart> parts = new ArrayList<>();
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getNodeValue();
                if (!text.isEmpty()) parts.add(new TimedPart(null, null, text));
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String role = role(childElement);
                if (!"x-translation".equals(role) && !"x-roman".equals(role)) {
                    Long begin = attrMillis(childElement, "begin");
                    Long end = attrMillis(childElement, "end");
                    parts.add(new TimedPart(
                            begin != null ? begin : defaultBegin,
                            end != null ? end : defaultEnd,
                            collectPlainTextSkippingRoles(childElement)
                    ));
                }
            }
            child = child.getNextSibling();
        }
        return new TimedText(parts);
    }

    private static String role(Element element) {
        String role = element.getAttributeNS("http://www.w3.org/ns/ttml#metadata", "role");
        if (role == null || role.isBlank()) role = element.getAttribute("ttm:role");
        return role == null || role.isBlank() ? null : role;
    }

    private static Long attrMillis(Element element, String name) {
        String value = element.getAttribute(name);
        return value == null || value.isBlank() ? null : parseMillis(value);
    }

    private static Long parseMillis(String value) {
        String clean = value.trim();
        if (clean.endsWith("s")) {
            try {
                return (long) (Double.parseDouble(clean.substring(0, clean.length() - 1)) * 1000);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        String[] parts = clean.split(":");
        try {
            if (parts.length == 1) return (long) (Double.parseDouble(parts[0]) * 1000);
            if (parts.length == 2) return (long) ((Long.parseLong(parts[0]) * 60 + Double.parseDouble(parts[1])) * 1000);
            if (parts.length == 3) {
                return (long) ((Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60 + Double.parseDouble(parts[2])) * 1000);
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    private static String formatTimestamp(long ms) {
        long safeMs = Math.max(0, ms);
        long minutes = safeMs / 60000;
        long seconds = safeMs % 60000 / 1000;
        long millis = safeMs % 1000;
        return String.format("[%02d:%02d.%03d]", minutes, seconds, millis);
    }

    private static String formatInlineTimestamp(long ms) {
        long safeMs = Math.max(0, ms);
        long minutes = safeMs / 60000;
        long seconds = safeMs % 60000 / 1000;
        long millis = safeMs % 1000;
        return String.format("<%02d:%02d.%03d>", minutes, seconds, millis);
    }

    private static String ensureParenthesized(String value) {
        String trimmed = value.trim();
        if ((trimmed.startsWith("(") && trimmed.endsWith(")")) || (trimmed.startsWith("（") && trimmed.endsWith("）"))) {
            return value;
        }
        return "(" + trimmed + ")";
    }

    private record SplLine(long start, Long end, String text, String subText, int priority) {
    }

    private record TimedText(List<TimedPart> parts) {
        String text() {
            StringBuilder text = new StringBuilder();
            for (TimedPart part : parts) text.append(part.text);
            return text.toString().trim();
        }

        String toSplText(long lineStart, Long lineEnd) {
            if (parts.stream().noneMatch(part -> part.begin != null)) return text();

            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                TimedPart part = parts.get(i);
                if (i > 0 && part.begin != null && part.begin > lineStart) {
                    builder.append(formatInlineTimestamp(part.begin));
                }
                builder.append(part.text);
            }
            return builder.toString().trim();
        }
    }

    private record TimedPart(Long begin, Long end, String text) {
    }
}
