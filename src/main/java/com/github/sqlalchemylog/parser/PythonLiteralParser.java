package com.github.sqlalchemylog.parser;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Python literal representations into structured parameters for SQL reconstruction.
 * Handles tuples, dicts, lists, None, booleans, strings, numbers, datetime, Decimal, UUID, bytes.
 */
public class PythonLiteralParser {

    private static final Pattern DATETIME_PATTERN = Pattern.compile(
            "datetime\\.datetime\\((\\d+),\\s*(\\d+),\\s*(\\d+)(?:,\\s*(\\d+))?(?:,\\s*(\\d+))?(?:,\\s*(\\d+))?(?:,\\s*(\\d+))?.*?\\)"
    );
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "datetime\\.date\\((\\d+),\\s*(\\d+),\\s*(\\d+)\\)"
    );
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "datetime\\.time\\((\\d+),\\s*(\\d+)(?:,\\s*(\\d+))?(?:,\\s*(\\d+))?.*?\\)"
    );
    private static final Pattern DECIMAL_PATTERN = Pattern.compile(
            "Decimal\\(['\"]?([^'\"]+)['\"]?\\)"
    );
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "UUID\\(['\"]?([a-fA-F0-9\\-]+)['\"]?\\)"
    );
    private static final Pattern TIMEDELTA_PATTERN = Pattern.compile(
            "datetime\\.timedelta\\((.*?)\\)"
    );
    private static final Pattern ENUM_PATTERN = Pattern.compile(
            "^<[a-zA-Z0-9_.]+\\s*:\\s*(.+)>$"
    );
    private static final Pattern IP_PATTERN = Pattern.compile(
            "^(?:IPv4Address|IPv6Address|IPv4Network|IPv6Network|IPv4Interface|IPv6Interface)\\(['\"]([^'\"]+)['\"]\\)$"
    );

    public static class ParsedParams {
        private final boolean isBatch;
        private final List<List<String>> positionalBatches;
        private final List<Map<String, String>> namedBatches;

        public ParsedParams(List<List<String>> positionalBatches, List<Map<String, String>> namedBatches) {
            this.positionalBatches = positionalBatches != null ? positionalBatches : Collections.emptyList();
            this.namedBatches = namedBatches != null ? namedBatches : Collections.emptyList();
            this.isBatch = (this.positionalBatches.size() > 1 || this.namedBatches.size() > 1);
        }

        public boolean isBatch() {
            return isBatch;
        }

        public boolean isNamed() {
            return !namedBatches.isEmpty();
        }

        public boolean isPositional() {
            return !positionalBatches.isEmpty();
        }

        public boolean isEmpty() {
            return positionalBatches.isEmpty() && namedBatches.isEmpty();
        }

        public List<List<String>> getPositionalBatches() {
            return positionalBatches;
        }

        public List<Map<String, String>> getNamedBatches() {
            return namedBatches;
        }

        public List<String> getFirstPositional() {
            return positionalBatches.isEmpty() ? Collections.emptyList() : positionalBatches.get(0);
        }

        public Map<String, String> getFirstNamed() {
            return namedBatches.isEmpty() ? Collections.emptyMap() : namedBatches.get(0);
        }
    }

    public static ParsedParams parse(String text) {
        if (text == null) {
            return new ParsedParams(Collections.emptyList(), Collections.emptyList());
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty() || "()".equals(trimmed) || "{}".equals(trimmed) || "[]".equals(trimmed)
                || trimmed.contains("[SQL parameters hidden")) {
            return new ParsedParams(Collections.emptyList(), Collections.emptyList());
        }

        // If batch list: [...]
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            List<Object> items = parseListElements(trimmed.substring(1, trimmed.length() - 1).trim());
            List<List<String>> posBatches = new ArrayList<>();
            List<Map<String, String>> namedBatches = new ArrayList<>();

            for (Object item : items) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> map = (Map<String, String>) item;
                    namedBatches.add(map);
                } else if (item instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> list = (List<String>) item;
                    posBatches.add(list);
                }
            }

            return new ParsedParams(posBatches, namedBatches);
        }

        // Single tuple: (...)
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            List<String> tupleItems = parseTupleElements(trimmed.substring(1, trimmed.length() - 1).trim());
            return new ParsedParams(Collections.singletonList(tupleItems), Collections.emptyList());
        }

        // Single dict: {...}
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            Map<String, String> dictItems = parseDictElements(trimmed.substring(1, trimmed.length() - 1).trim());
            return new ParsedParams(Collections.emptyList(), Collections.singletonList(dictItems));
        }

        return new ParsedParams(Collections.emptyList(), Collections.emptyList());
    }

    private static List<String> parseTupleElements(String content) {
        List<String> result = new ArrayList<>();
        if (content.isEmpty()) {
            return result;
        }

        List<String> tokens = splitTopLevel(content, ',');
        for (String tok : tokens) {
            String item = tok.trim();
            if (!item.isEmpty()) {
                result.add(formatToSqlLiteral(item));
            }
        }
        return result;
    }

    private static Map<String, String> parseDictElements(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        if (content.isEmpty()) {
            return result;
        }

        List<String> pairs = splitTopLevel(content, ',');
        for (String pair : pairs) {
            String trimmedPair = pair.trim();
            if (trimmedPair.isEmpty()) {
                continue;
            }
            int colonIndex = findTopLevelColon(trimmedPair);
            if (colonIndex != -1) {
                String keyStr = trimmedPair.substring(0, colonIndex).trim();
                String valStr = trimmedPair.substring(colonIndex + 1).trim();

                // Strip quotes from key
                String key = unquote(keyStr);
                String sqlVal = formatToSqlLiteral(valStr);
                result.put(key, sqlVal);
            }
        }
        return result;
    }

    private static List<Object> parseListElements(String content) {
        List<Object> result = new ArrayList<>();
        if (content.isEmpty()) {
            return result;
        }

        List<String> elements = splitTopLevel(content, ',');
        for (String elem : elements) {
            String trimmed = elem.trim();
            if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
                result.add(parseTupleElements(trimmed.substring(1, trimmed.length() - 1).trim()));
            } else if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                result.add(parseDictElements(trimmed.substring(1, trimmed.length() - 1).trim()));
            } else if (!trimmed.isEmpty()) {
                result.add(Collections.singletonList(formatToSqlLiteral(trimmed)));
            }
        }
        return result;
    }

    public static String formatToSqlLiteral(String token) {
        if (token == null) {
            return "NULL";
        }
        String trimmed = token.trim();
        if (trimmed.isEmpty() || "None".equals(trimmed)) {
            return "NULL";
        }
        if ("True".equals(trimmed)) {
            return "1";
        }
        if ("False".equals(trimmed)) {
            return "0";
        }

        // Check datetime.datetime(...)
        Matcher dtMatcher = DATETIME_PATTERN.matcher(trimmed);
        if (dtMatcher.find()) {
            int year = Integer.parseInt(dtMatcher.group(1));
            int month = Integer.parseInt(dtMatcher.group(2));
            int day = Integer.parseInt(dtMatcher.group(3));
            int hour = dtMatcher.group(4) != null ? Integer.parseInt(dtMatcher.group(4)) : 0;
            int minute = dtMatcher.group(5) != null ? Integer.parseInt(dtMatcher.group(5)) : 0;
            int second = dtMatcher.group(6) != null ? Integer.parseInt(dtMatcher.group(6)) : 0;
            int micro = dtMatcher.group(7) != null ? Integer.parseInt(dtMatcher.group(7)) : 0;

            if (micro > 0) {
                return String.format("'%04d-%02d-%02d %02d:%02d:%02d.%06d'", year, month, day, hour, minute, second, micro);
            } else {
                return String.format("'%04d-%02d-%02d %02d:%02d:%02d'", year, month, day, hour, minute, second);
            }
        }

        // Check datetime.date(...)
        Matcher dateMatcher = DATE_PATTERN.matcher(trimmed);
        if (dateMatcher.find()) {
            int year = Integer.parseInt(dateMatcher.group(1));
            int month = Integer.parseInt(dateMatcher.group(2));
            int day = Integer.parseInt(dateMatcher.group(3));
            return String.format("'%04d-%02d-%02d'", year, month, day);
        }

        // Check datetime.time(...)
        Matcher timeMatcher = TIME_PATTERN.matcher(trimmed);
        if (timeMatcher.find()) {
            int hour = Integer.parseInt(timeMatcher.group(1));
            int minute = Integer.parseInt(timeMatcher.group(2));
            int second = timeMatcher.group(3) != null ? Integer.parseInt(timeMatcher.group(3)) : 0;
            int micro = timeMatcher.group(4) != null ? Integer.parseInt(timeMatcher.group(4)) : 0;
            if (micro > 0) {
                return String.format("'%02d:%02d:%02d.%06d'", hour, minute, second, micro);
            } else {
                return String.format("'%02d:%02d:%02d'", hour, minute, second);
            }
        }

        // Check Decimal(...)
        Matcher decMatcher = DECIMAL_PATTERN.matcher(trimmed);
        if (decMatcher.find()) {
            return decMatcher.group(1);
        }

        // Check UUID(...)
        Matcher uuidMatcher = UUID_PATTERN.matcher(trimmed);
        if (uuidMatcher.find()) {
            return "'" + uuidMatcher.group(1) + "'";
        }

        // Check timedelta(...)
        Matcher tdMatcher = TIMEDELTA_PATTERN.matcher(trimmed);
        if (tdMatcher.find()) {
            return parseTimedelta(tdMatcher.group(1));
        }

        // Check Enum: <Role.ADMIN: 'admin'> or <Level.ONE: 1>
        Matcher enumMatcher = ENUM_PATTERN.matcher(trimmed);
        if (enumMatcher.find()) {
            return formatToSqlLiteral(enumMatcher.group(1));
        }

        // Check IP: IPv4Address('192.168.1.1')
        Matcher ipMatcher = IP_PATTERN.matcher(trimmed);
        if (ipMatcher.find()) {
            return "'" + ipMatcher.group(1) + "'";
        }

        // Check bytearray(b'...') or bytearray(b"...")
        if (trimmed.startsWith("bytearray(") && trimmed.endsWith(")")) {
            String inner = trimmed.substring("bytearray(".length(), trimmed.length() - 1).trim();
            return formatToSqlLiteral(inner);
        }

        // Check <memory at 0x...>
        if (trimmed.startsWith("<memory at ") || trimmed.startsWith("<memoryview")) {
            return "'<binary>'";
        }

        // Check bytes: b'...' or b"..."
        if (trimmed.startsWith("b'") && trimmed.endsWith("'") && trimmed.length() >= 3) {
            String content = trimmed.substring(2, trimmed.length() - 1);
            return "'" + escapeSqlString(unescapePythonString(content)) + "'";
        }
        if (trimmed.startsWith("b\"") && trimmed.endsWith("\"") && trimmed.length() >= 3) {
            String content = trimmed.substring(2, trimmed.length() - 1);
            return "'" + escapeSqlString(unescapePythonString(content)) + "'";
        }

        // String literals: '...' or "..."
        if ((trimmed.startsWith("'") && trimmed.endsWith("'")) || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
            if (trimmed.length() >= 2) {
                String inner = trimmed.substring(1, trimmed.length() - 1);
                String unescaped = unescapePythonString(inner);
                return "'" + escapeSqlString(unescaped) + "'";
            }
        }

        // Dict / JSON: {'a': 1, 'b': 'val'}
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            String json = convertPythonToJson(trimmed);
            return "'" + escapeSqlString(json) + "'";
        }

        // List / Array: [1, 2, 'three']
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            String json = convertPythonToJson(trimmed);
            return "'" + escapeSqlString(json) + "'";
        }

        // Numeric literals: integer, float, hex
        if (isNumeric(trimmed)) {
            return trimmed;
        }

        // Default fallback: wrap in quotes or as literal
        return "'" + escapeSqlString(trimmed) + "'";
    }

    private static String parseTimedelta(String content) {
        long days = 0;
        long seconds = 0;
        long micros = 0;

        if (content.contains("=")) {
            for (String part : content.split(",")) {
                String[] kv = part.split("=");
                if (kv.length == 2) {
                    String k = kv[0].trim();
                    long v = 0;
                    try {
                        v = Long.parseLong(kv[1].trim());
                    } catch (NumberFormatException ignored) {}
                    if ("days".equals(k)) days = v;
                    else if ("seconds".equals(k)) seconds = v;
                    else if ("microseconds".equals(k)) micros = v;
                }
            }
        } else if (!content.trim().isEmpty()) {
            String[] parts = content.split(",");
            if (parts.length > 0) {
                try { days = Long.parseLong(parts[0].trim()); } catch (Exception ignored) {}
            }
            if (parts.length > 1) {
                try { seconds = Long.parseLong(parts[1].trim()); } catch (Exception ignored) {}
            }
            if (parts.length > 2) {
                try { micros = Long.parseLong(parts[2].trim()); } catch (Exception ignored) {}
            }
        }

        long hours = seconds / 3600;
        long remSec = seconds % 3600;
        long minutes = remSec / 60;
        long secs = remSec % 60;

        StringBuilder sb = new StringBuilder("'");
        if (days != 0) {
            sb.append(days).append(Math.abs(days) == 1 ? " day " : " days ");
        }
        sb.append(String.format("%02d:%02d:%02d", hours, minutes, secs));
        if (micros > 0) {
            sb.append(String.format(".%06d", micros));
        }
        sb.append("'");
        return sb.toString();
    }

    public static String convertPythonToJson(String token) {
        String trimmed = token.trim();
        if (trimmed.isEmpty() || "None".equals(trimmed)) {
            return "null";
        }
        if ("True".equals(trimmed)) {
            return "true";
        }
        if ("False".equals(trimmed)) {
            return "false";
        }
        if (isNumeric(trimmed)) {
            return trimmed;
        }
        if ((trimmed.startsWith("'") && trimmed.endsWith("'")) || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
            if (trimmed.length() >= 2) {
                String inner = trimmed.substring(1, trimmed.length() - 1);
                String unescaped = unescapePythonString(inner);
                return "\"" + escapeJsonString(unescaped) + "\"";
            }
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            String inner = trimmed.substring(1, trimmed.length() - 1).trim();
            if (inner.isEmpty()) return "{}";
            List<String> pairs = splitTopLevel(inner, ',');
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (String pair : pairs) {
                String tp = pair.trim();
                if (tp.isEmpty()) continue;
                int colonIdx = findTopLevelColon(tp);
                if (colonIdx != -1) {
                    String k = tp.substring(0, colonIdx).trim();
                    String v = tp.substring(colonIdx + 1).trim();
                    if (!first) sb.append(", ");
                    first = false;
                    sb.append("\"").append(escapeJsonString(unquote(k))).append("\": ");
                    sb.append(convertPythonToJson(v));
                }
            }
            sb.append("}");
            return sb.toString();
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            String inner = trimmed.substring(1, trimmed.length() - 1).trim();
            if (inner.isEmpty()) return "[]";
            List<String> elements = splitTopLevel(inner, ',');
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (String elem : elements) {
                String te = elem.trim();
                if (te.isEmpty()) continue;
                if (!first) sb.append(", ");
                first = false;
                sb.append(convertPythonToJson(te));
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeJsonString(trimmed) + "\"";
    }

    private static String escapeJsonString(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    private static String unquote(String str) {
        if ((str.startsWith("'") && str.endsWith("'")) || (str.startsWith("\"") && str.endsWith("\""))) {
            if (str.length() >= 2) {
                return str.substring(1, str.length() - 1);
            }
        }
        return str;
    }

    private static String unescapePythonString(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean escaped = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case '\\': sb.append('\\'); break;
                    case '\'': sb.append('\''); break;
                    case '"': sb.append('"'); break;
                    default:
                        sb.append(c);
                        break;
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String escapeSqlString(String s) {
        return s.replace("'", "''");
    }

    private static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static List<String> splitTopLevel(String content, char delimiter) {
        List<String> tokens = new ArrayList<>();
        int depthParen = 0;
        int depthBracket = 0;
        int depthBrace = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaped = false;
        int start = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }

            if (!inSingleQuote && !inDoubleQuote) {
                if (c == '(') depthParen++;
                else if (c == ')') depthParen--;
                else if (c == '[') depthBracket++;
                else if (c == ']') depthBracket--;
                else if (c == '{') depthBrace++;
                else if (c == '}') depthBrace--;
                else if (c == delimiter && depthParen == 0 && depthBracket == 0 && depthBrace == 0) {
                    tokens.add(content.substring(start, i));
                    start = i + 1;
                }
            }
        }

        if (start < content.length()) {
            tokens.add(content.substring(start));
        }

        return tokens;
    }

    private static int findTopLevelColon(String content) {
        int depthParen = 0;
        int depthBracket = 0;
        int depthBrace = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaped = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }

            if (!inSingleQuote && !inDoubleQuote) {
                if (c == '(') depthParen++;
                else if (c == ')') depthParen--;
                else if (c == '[') depthBracket++;
                else if (c == ']') depthBracket--;
                else if (c == '{') depthBrace++;
                else if (c == '}') depthBrace--;
                else if (c == ':' && depthParen == 0 && depthBracket == 0 && depthBrace == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
