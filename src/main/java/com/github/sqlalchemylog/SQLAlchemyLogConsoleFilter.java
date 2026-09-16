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

    // Matches message start after engine: either [timing/param] or SQL/Tx keyword
    private static final Pattern AFTER_ENGINE_PATTERN = Pattern.compile(
            "(\\[[^\\]]*\\]\\s*.*|\\b(?:SELECT|INSERT|UPDATE|DELETE|CREATE|DROP|ALTER|PRAGMA|TRUNCATE|MERGE|EXPLAIN|WITH|SHOW|SET|BEGIN|COMMIT|ROLLBACK|SAVEPOINT|RELEASE)\\b.*)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Matches parameter lines like [generated in 0.00018s] ('Alice', 18) or [raw sql] {}
    private static final Pattern PARAM_LINE_PATTERN = Pattern.compile("^(\\[[^\\]]+\\])\\s*(.*)", Pattern.DOTALL);

    private static final Pattern TX_PATTERN = Pattern.compile(
            "^(BEGIN(?:\\s*\\([^)]*\\))?|COMMIT|ROLLBACK|SAVEPOINT\\s+\\w+|RELEASE\\s+SAVEPOINT\\s+\\w+)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SQL_START_PATTERN = Pattern.compile(
            "^(SELECT|INSERT|UPDATE|DELETE|CREATE|DROP|ALTER|PRAGMA|TRUNCATE|MERGE|EXPLAIN|WITH|SHOW|SET)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final Project project;

    private final StringBuilder pendingSql = new StringBuilder();
    private String lastLogPrefix = "";

    public SQLAlchemyLogConsoleFilter(Project project) {
        this.project = project;
    }

    @Override
    public synchronized @Nullable Result applyFilter(@NotNull String line, int entireLength) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String enginePrefix = getEnginePrefix();
        int engineIdx = line.toLowerCase(Locale.ROOT).indexOf(enginePrefix.toLowerCase(Locale.ROOT));

        String content;
        String logPrefix = "";
        boolean isEngineLine = (engineIdx != -1);

        if (isEngineLine) {
            String afterEngine = line.substring(engineIdx + enginePrefix.length());
            Matcher matcher = AFTER_ENGINE_PATTERN.matcher(afterEngine);
            if (matcher.find()) {
                int startOffset = matcher.start();
                content = afterEngine.substring(startOffset).trim();
                logPrefix = line.substring(0, engineIdx + enginePrefix.length() + startOffset).trim();
                logPrefix = cleanPrefix(logPrefix);
            } else {
                content = afterEngine.trim();
                logPrefix = line.substring(0, engineIdx + enginePrefix.length()).trim();
            }
        } else {
            content = trimmed;
        }

        // If not an engine line and no query currently pending, ignore
        if (!isEngineLine && pendingSql.length() == 0) {
            return null;
        }

        // Keyword exclusion check
        List<String> keywords = getKeywords();
        for (String kw : keywords) {
            if (!kw.isEmpty() && (line.contains(kw) || content.contains(kw))) {
                pendingSql.setLength(0);
                return null;
            }
        }

        // Lazy auto-initialize manager so user does NOT have to manually click Tools -> ...
        final SQLAlchemyLogManager manager = SQLAlchemyLogManager.getInstanceOrCreate(project);
        if (!manager.isRunning()) {
            return null;
        }

        // Check transaction commands (BEGIN, COMMIT, ROLLBACK)
        Matcher txMatcher = TX_PATTERN.matcher(content);
        if (isEngineLine && txMatcher.matches()) {
            if (pendingSql.length() > 0) {
                flushPendingSql(manager, lastLogPrefix);
            }
            int txColor = PropertiesComponent.getInstance(project).getInt(
                    TX_SQL_COLOR_KEY,
                    new JBColor(0x616161, 0x808080).getRGB()
            );
            manager.println(logPrefix, content, txColor);
            return null;
        }

        // Check parameter line: starts with [ or matches PARAM_LINE_PATTERN
        Matcher paramMatcher = PARAM_LINE_PATTERN.matcher(content);
        if (paramMatcher.find()) {
            String timingInfo = paramMatcher.group(1);
            String paramLiteral = paramMatcher.group(2).trim();

            if (pendingSql.length() > 0) {
                PythonLiteralParser.ParsedParams parsedParams = PythonLiteralParser.parse(paramLiteral);
                List<String> restoredQueries = SQLAlchemySqlParser.restoreSql(pendingSql.toString(), parsedParams);

                String fullPrefix = lastLogPrefix;
                if (timingInfo != null && !timingInfo.isEmpty()) {
                    fullPrefix = fullPrefix.isEmpty() ? timingInfo : (fullPrefix + " " + timingInfo);
                }

                for (String restored : restoredQueries) {
                    int color = getSqlColor(restored);
                    manager.println(fullPrefix, restored, color);
                }

                pendingSql.setLength(0);
                lastLogPrefix = "";
                return null;
            }
        }

        // If it's a new SQL query line
        if (isEngineLine && isSqlStart(content)) {
            if (pendingSql.length() > 0) {
                flushPendingSql(manager, lastLogPrefix);
            }
            pendingSql.append(content);
            lastLogPrefix = logPrefix;
            return null;
        }

        // If currently accumulating multi-line SQL
        if (pendingSql.length() > 0) {
            pendingSql.append("\n").append(content);
            return null;
        }

        return null;
    }

    private void flushPendingSql(SQLAlchemyLogManager manager, String prefix) {
        if (pendingSql.length() == 0) {
            return;
        }
        String sql = pendingSql.toString().trim();
        if (!sql.isEmpty()) {
            int color = getSqlColor(sql);
            manager.println(prefix, sql, color);
        }
        pendingSql.setLength(0);
    }

    private boolean isSqlStart(String content) {
        return SQL_START_PATTERN.matcher(content).find();
    }

    private String cleanPrefix(String prefix) {
        String p = prefix.trim();
        while (p.endsWith(":") || p.endsWith("-") || p.endsWith("]")) {
            p = p.substring(0, p.length() - 1).trim();
        }
        return p;
    }

    private String getEnginePrefix() {
        return PropertiesComponent.getInstance(project).getValue(ENGINE_PREFIX_KEY, "sqlalchemy.engine");
    }

    private List<String> getKeywords() {
        SQLAlchemyLogManager manager = SQLAlchemyLogManager.getInstance(project);
        if (manager != null) {
            return manager.getKeywords();
        }
        return List.of();
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
