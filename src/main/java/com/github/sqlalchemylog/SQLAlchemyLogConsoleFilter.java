package com.github.sqlalchemylog;

import com.github.sqlalchemylog.gui.SQLAlchemyLogManager;
import com.github.sqlalchemylog.parser.PythonLiteralParser;
import com.github.sqlalchemylog.parser.SQLAlchemySqlParser;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SQLAlchemyLogConsoleFilter implements Filter {

    public static final String ENGINE_PREFIX_KEY = "SQLAlchemyLog.EnginePrefix";
    public static final String PARAMETERS_PREFIX_KEY = "SQLAlchemyLog.ParametersPrefix";
    public static final String KEYWORDS_KEY = "SQLAlchemyLog.Keywords";

    public static final String INSERT_SQL_COLOR_KEY = "SQLAlchemyLog.InsertSQLColor";
    public static final String DELETE_SQL_COLOR_KEY = "SQLAlchemyLog.DeleteSQLColor";
    public static final String UPDATE_SQL_COLOR_KEY = "SQLAlchemyLog.UpdateSQLColor";
    public static final String SELECT_SQL_COLOR_KEY = "SQLAlchemyLog.SelectSQLColor";
    public static final String TX_SQL_COLOR_KEY = "SQLAlchemyLog.TxSQLColor";

    // Pattern to match parameter lines like [generated in 0.00018s] ('Alice', 18) or [raw sql] ()
    private static final Pattern PARAM_LINE_PATTERN = Pattern.compile("(\\[[^\\]]+\\])\\s*(.*)");

    private static final Pattern TX_PATTERN = Pattern.compile(
            "\\b(BEGIN(?:\\s*\\([^)]*\\))?|COMMIT|ROLLBACK|SAVEPOINT\\s+\\w+|RELEASE\\s+SAVEPOINT\\s+\\w+)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern NEW_QUERY_START = Pattern.compile(
            "^(SELECT|INSERT|UPDATE|DELETE|CREATE|DROP|ALTER|PRAGMA|TRUNCATE|MERGE|EXPLAIN|WITH|SHOW|SET)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final Project project;

    private StringBuilder pendingSql = null;
    private String lastLogPrefix = "";

    public SQLAlchemyLogConsoleFilter(Project project) {
        this.project = project;
    }

    @Override
    public @Nullable Result applyFilter(@NotNull String line, int entireLength) {
        final SQLAlchemyLogManager manager = SQLAlchemyLogManager.getInstance(project);
        if (manager == null || !manager.isRunning()) {
            return null;
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // Keyword exclusion check
        List<String> keywords = manager.getKeywords();
        for (String kw : keywords) {
            if (!kw.isEmpty() && line.contains(kw)) {
                pendingSql = null;
                return null;
            }
        }

        String enginePrefix = manager.getEnginePrefix();
        boolean hasEnginePrefix = line.contains(enginePrefix);

        String contentAfterPrefix = line;
        String logPrefix = "";
        if (hasEnginePrefix) {
            int idx = line.indexOf(enginePrefix);
            int colonIdx = line.indexOf(':', idx + enginePrefix.length());
            if (colonIdx != -1) {
                logPrefix = line.substring(0, colonIdx + 1).trim();
                contentAfterPrefix = line.substring(colonIdx + 1).trim();
            } else {
                logPrefix = line.substring(0, idx + enginePrefix.length()).trim();
                contentAfterPrefix = line.substring(idx + enginePrefix.length()).trim();
            }
        }

        // Check transaction commands (BEGIN, COMMIT, ROLLBACK)
        Matcher txMatcher = TX_PATTERN.matcher(contentAfterPrefix);
        if (hasEnginePrefix && txMatcher.matches()) {
            if (pendingSql != null && pendingSql.length() > 0) {
                flushPendingSql(manager, lastLogPrefix);
            }
            int txColor = PropertiesComponent.getInstance(project).getInt(
                    TX_SQL_COLOR_KEY,
                    new JBColor(0x616161, 0x808080).getRGB()
            );
            manager.println(logPrefix, contentAfterPrefix, txColor);
            return null;
        }

        // Check if this is a parameter line: e.g. [generated in 0.00018s] ('Alice', 18)
        String paramIndicator = manager.getParametersPrefix();
        boolean isParamLine = contentAfterPrefix.startsWith(paramIndicator) || contentAfterPrefix.contains(paramIndicator);

        if (isParamLine) {
            Matcher paramMatcher = PARAM_LINE_PATTERN.matcher(contentAfterPrefix);
            String timingInfo = "";
            String paramLiteral = "";

            if (paramMatcher.find()) {
                timingInfo = paramMatcher.group(1);
                paramLiteral = paramMatcher.group(2).trim();
            } else if (contentAfterPrefix.startsWith("[")) {
                int closeBracket = contentAfterPrefix.indexOf(']');
                if (closeBracket != -1) {
                    timingInfo = contentAfterPrefix.substring(0, closeBracket + 1);
                    paramLiteral = contentAfterPrefix.substring(closeBracket + 1).trim();
                }
            }

            if (pendingSql != null && pendingSql.length() > 0) {
                PythonLiteralParser.ParsedParams parsedParams = PythonLiteralParser.parse(paramLiteral);
                List<String> restoredQueries = SQLAlchemySqlParser.restoreSql(pendingSql.toString(), parsedParams);

                String fullPrefix = lastLogPrefix;
                if (!timingInfo.isEmpty()) {
                    fullPrefix = fullPrefix.isEmpty() ? timingInfo : (fullPrefix + " " + timingInfo);
                }

                for (String restored : restoredQueries) {
                    int color = getSqlColor(restored);
                    manager.println(fullPrefix, restored, color);
                }

                pendingSql = null;
                lastLogPrefix = "";
                return null;
            }
        }

        // If it starts a new SQL query
        String potentialSql = contentAfterPrefix.trim();
        Matcher newQueryMatcher = NEW_QUERY_START.matcher(potentialSql);

        if (hasEnginePrefix && newQueryMatcher.find()) {
            // Flush any previous query if still pending
            if (pendingSql != null && pendingSql.length() > 0) {
                flushPendingSql(manager, lastLogPrefix);
            }
            pendingSql = new StringBuilder(potentialSql);
            lastLogPrefix = logPrefix;
            return null;
        }

        // If we are currently collecting a multi-line SQL statement
        if (pendingSql != null) {
            if (hasEnginePrefix) {
                pendingSql.append("\n").append(contentAfterPrefix);
            } else {
                pendingSql.append("\n").append(line.trim());
            }
            return null;
        }

        return null;
    }

    private void flushPendingSql(SQLAlchemyLogManager manager, String prefix) {
        if (pendingSql == null || pendingSql.length() == 0) {
            return;
        }
        String sql = pendingSql.toString().trim();
        if (!sql.isEmpty()) {
            int color = getSqlColor(sql);
            manager.println(prefix, sql, color);
        }
        pendingSql = null;
    }

    private int getSqlColor(String sql) {
        final PropertiesComponent props = PropertiesComponent.getInstance(project);
        String upper = sql.trim().toUpperCase(Locale.ROOT);

        if (upper.startsWith("SELECT")) {
            return props.getInt(SELECT_SQL_COLOR_KEY, new JBColor(0x0066CC, 0x589DF6).getRGB());
        } else if (upper.startsWith("INSERT")) {
            return props.getInt(INSERT_SQL_COLOR_KEY, new JBColor(0x2E7D32, 0x499C54).getRGB());
        } else if (upper.startsWith("UPDATE")) {
            return props.getInt(UPDATE_SQL_COLOR_KEY, new JBColor(0xE65100, 0xCC7832).getRGB());
        } else if (upper.startsWith("DELETE")) {
            return props.getInt(DELETE_SQL_COLOR_KEY, new JBColor(0xC62828, 0xBC3F3C).getRGB());
        } else if (upper.startsWith("BEGIN") || upper.startsWith("COMMIT") || upper.startsWith("ROLLBACK")) {
            return props.getInt(TX_SQL_COLOR_KEY, new JBColor(0x616161, 0x808080).getRGB());
        } else {
            return ConsoleViewContentType.NORMAL_OUTPUT.getAttributes().getForegroundColor() != null ?
                    ConsoleViewContentType.NORMAL_OUTPUT.getAttributes().getForegroundColor().getRGB() :
                    new JBColor(0x0066CC, 0x589DF6).getRGB();
        }
    }
}
