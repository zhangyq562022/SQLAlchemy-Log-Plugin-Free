package com.github.sqlalchemylog.parser;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces placeholders in SQLAlchemy SQL statements with evaluated Python parameter literals.
 * Supports:
 * - ? (qmark)
 * - %s (format)
 * - %(name)s (pyformat)
 * - :name (named, avoiding ::type cast)
 * - $1, $2 (numeric)
 * - executemany batch expansions
 */
public class SQLAlchemySqlParser {

    private static final Pattern PYFORMAT_PATTERN = Pattern.compile("%\\(([a-zA-Z0-9_]+)\\)s");
    private static final Pattern NAMED_PATTERN = Pattern.compile("(?<!:):([a-zA-Z0-9_]+)\\b");
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("\\$([1-9][0-9]*)\\b");

    public static List<String> restoreSql(String sql, PythonLiteralParser.ParsedParams params) {
        if (sql == null) {
            return Collections.emptyList();
        }
        String cleanSql = sql.trim();
        if (cleanSql.isEmpty()) {
            return Collections.emptyList();
        }

        if (params == null || params.isEmpty()) {
            return Collections.singletonList(cleanSql);
        }

        // Executemany batch case
        if (params.isBatch()) {
            List<String> results = new ArrayList<>();
            if (params.isNamed()) {
                for (Map<String, String> batchItem : params.getNamedBatches()) {
                    results.add(restoreSingleSql(cleanSql, Collections.emptyList(), batchItem));
                }
            } else if (params.isPositional()) {
                for (List<String> batchItem : params.getPositionalBatches()) {
                    results.add(restoreSingleSql(cleanSql, batchItem, Collections.emptyMap()));
                }
            }
            return results;
        }

        // Single execution case
        String singleResult = restoreSingleSql(cleanSql, params.getFirstPositional(), params.getFirstNamed());
        return Collections.singletonList(singleResult);
    }

    private static String restoreSingleSql(String sql, List<String> positionalParams, Map<String, String> namedParams) {
        if (!namedParams.isEmpty()) {
            // First try %(name)s
            Matcher pyformatMatcher = PYFORMAT_PATTERN.matcher(sql);
            if (pyformatMatcher.find()) {
                StringBuffer sb = new StringBuffer();
                pyformatMatcher.reset();
                while (pyformatMatcher.find()) {
                    String paramName = pyformatMatcher.group(1);
                    String val = namedParams.getOrDefault(paramName, "NULL");
                    pyformatMatcher.appendReplacement(sb, Matcher.quoteReplacement(val));
                }
                pyformatMatcher.appendTail(sb);
                sql = sb.toString();
            }

            // Next try :name (outside quotes and not ::cast)
            sql = replaceNamedParams(sql, namedParams);
        }

        if (!positionalParams.isEmpty()) {
            // Check for numeric $1, $2
            Matcher numMatcher = NUMERIC_PATTERN.matcher(sql);
            if (numMatcher.find()) {
                StringBuffer sb = new StringBuffer();
                numMatcher.reset();
                while (numMatcher.find()) {
                    int index = Integer.parseInt(numMatcher.group(1)) - 1;
                    String val = (index >= 0 && index < positionalParams.size()) ? positionalParams.get(index) : "NULL";
                    numMatcher.appendReplacement(sb, Matcher.quoteReplacement(val));
                }
                numMatcher.appendTail(sb);
                sql = sb.toString();
            } else {
                // Check for ? (qmark) or %s (format)
                sql = replacePositionalPlaceholders(sql, positionalParams);
            }
        }

        return sql;
    }

    private static String replaceNamedParams(String sql, Map<String, String> namedParams) {
        StringBuilder result = new StringBuilder();
        int len = sql.length();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < len; i++) {
            char c = sql.charAt(i);

            if (c == '\'' && !inDoubleQuote) {
                // Check if escaped '' inside single quote
                if (inSingleQuote && i + 1 < len && sql.charAt(i + 1) == '\'') {
                    result.append("''");
                    i++;
                    continue;
                }
                inSingleQuote = !inSingleQuote;
                result.append(c);
                continue;
            }

            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                result.append(c);
                continue;
            }

            if (!inSingleQuote && !inDoubleQuote) {
                if (c == ':' && i + 1 < len) {
                    // Check if it's ::cast (PostgreSQL type cast)
                    if (sql.charAt(i + 1) == ':') {
                        result.append("::");
                        i++;
                        continue;
                    }

                    // Check if previous char was ':'
                    if (i > 0 && sql.charAt(i - 1) == ':') {
                        result.append(c);
                        continue;
                    }

                    // Parse parameter name
                    int nameStart = i + 1;
                    int nameEnd = nameStart;
                    while (nameEnd < len && isIdentifierChar(sql.charAt(nameEnd))) {
                        nameEnd++;
                    }

                    if (nameEnd > nameStart) {
                        String paramName = sql.substring(nameStart, nameEnd);
                        if (namedParams.containsKey(paramName)) {
                            result.append(namedParams.get(paramName));
                            i = nameEnd - 1;
                            continue;
                        }
                    }
                }
            }

            result.append(c);
        }

        return result.toString();
    }

    private static String replacePositionalPlaceholders(String sql, List<String> positionalParams) {
        StringBuilder result = new StringBuilder();
        int len = sql.length();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int paramIdx = 0;

        for (int i = 0; i < len; i++) {
            char c = sql.charAt(i);

            if (c == '\'' && !inDoubleQuote) {
                if (inSingleQuote && i + 1 < len && sql.charAt(i + 1) == '\'') {
                    result.append("''");
                    i++;
                    continue;
                }
                inSingleQuote = !inSingleQuote;
                result.append(c);
                continue;
            }

            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                result.append(c);
                continue;
            }

            if (!inSingleQuote && !inDoubleQuote) {
                // Qmark placeholder '?'
                if (c == '?') {
                    if (paramIdx < positionalParams.size()) {
                        result.append(positionalParams.get(paramIdx++));
                    } else {
                        result.append("?");
                    }
                    continue;
                }

                // Format placeholder '%s' (skip %% which is escaped percent)
                if (c == '%' && i + 1 < len) {
                    if (sql.charAt(i + 1) == '%') {
                        result.append("%%");
                        i++;
                        continue;
                    } else if (sql.charAt(i + 1) == 's') {
                        if (paramIdx < positionalParams.size()) {
                            result.append(positionalParams.get(paramIdx++));
                        } else {
                            result.append("%s");
                        }
                        i++;
                        continue;
                    }
                }
            }

            result.append(c);
        }

        return result.toString();
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
