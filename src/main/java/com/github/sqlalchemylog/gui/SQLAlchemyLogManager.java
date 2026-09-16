package com.github.sqlalchemylog.gui;

import com.github.sqlalchemylog.Icons;
import com.github.sqlalchemylog.action.*;
import com.github.sqlalchemylog.format.BasicFormatter;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.content.Content;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.sqlalchemylog.SQLAlchemyLogConsoleFilter.*;

public class SQLAlchemyLogManager implements Disposable {

    private static final Key<SQLAlchemyLogManager> KEY = Key.create(SQLAlchemyLogManager.class.getName());
    private static final BasicFormatter FORMATTER = new BasicFormatter();

    private final Map<Integer, ConsoleViewContentType> consoleViewContentTypes = new ConcurrentHashMap<>();

    private final ConsoleView consoleView;
    private final Project project;
    private final RunContentDescriptor descriptor;

    private final AtomicInteger counter = new AtomicInteger();
    private volatile String enginePrefix;
    private volatile String parametersPrefix;
    private volatile boolean running = true;

    private final List<String> keywords = new ArrayList<>();

    private SQLAlchemyLogManager(@NotNull Project project) {
        this.project = project;

        this.consoleView = createConsoleView();

        final JPanel panel = createConsolePanel(this.consoleView);

        RunnerLayoutUi layoutUi = getRunnerLayoutUi();

        Content content = layoutUi.createContent(UUID.randomUUID().toString(), panel, "SQL", Icons.SQLALCHEMY, panel);
        content.setCloseable(false);
        layoutUi.addContent(content);

        layoutUi.getOptions().setLeftToolbar(createActionToolbar(), "RunnerToolbar");

        final MessageBusConnection messageBusConnection = project.getMessageBus().connect();

        this.descriptor = getRunContentDescriptor(layoutUi);

        Disposer.register(this, consoleView);
        Disposer.register(this, content);
        Disposer.register(this, layoutUi.getContentManager());
        Disposer.register(this, messageBusConnection);
        Disposer.register(project, this);

        final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(project);
        this.enginePrefix = propertiesComponent.getValue(ENGINE_PREFIX_KEY, "sqlalchemy.engine");
        this.parametersPrefix = propertiesComponent.getValue(PARAMETERS_PREFIX_KEY, "[");
        resetKeywords(propertiesComponent.getValue(KEYWORDS_KEY, "PRAGMA\ntable_info"));

        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed() || Disposer.isDisposed(this)) {
                return;
            }
            ExecutionManager.getInstance(project).getContentManager().showRunContent(
                    SQLAlchemyLogExecutor.getInstance(),
                    descriptor
            );
            ToolWindow tw = getToolWindow();
            if (tw != null) {
                tw.activate(null);
            }
        }, ModalityState.any());
    }

    public static Editor getEditor(ConsoleView consoleView) {
        if (consoleView == null) return null;
        if (consoleView instanceof DataProvider) {
            Editor editor = CommonDataKeys.EDITOR.getData((DataProvider) consoleView);
            if (editor != null) return editor;
        }
        try {
            java.lang.reflect.Method m = consoleView.getClass().getMethod("getEditor");
            return (Editor) m.invoke(consoleView);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private ConsoleView createConsoleView() {
        TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
        final ConsoleView console = consoleBuilder.getConsole();
        console.getComponent();

        final Editor editor = getEditor(console);
        if (editor != null) {
            editor.getDocument().addDocumentListener(new RangeHighlighterDocumentListener(editor));
        }

        return console;
    }

    private ActionGroup createActionToolbar() {
        final ConsoleView console = this.consoleView;
        final Editor editor = getEditor(console);
        final DefaultActionGroup actionGroup = new DefaultActionGroup();

        actionGroup.add(new RerunAction());
        actionGroup.add(new StopAction(this));
        actionGroup.add(new SettingsAction(this));
        actionGroup.addSeparator();
        actionGroup.add(new PreviousSqlAction(console, editor));
        actionGroup.add(new NextSqlAction(console, editor));
        actionGroup.addSeparator();

        for (AnAction action : console.createConsoleActions()) {
            String name = action.getClass().getSimpleName();
            if (name.contains("SoftWrap") || name.contains("ScrollToTheEnd")) {
                actionGroup.add(action);
            }
        }

        actionGroup.add(new PrettyPrintToggleAction());
        actionGroup.addSeparator();
        actionGroup.add(new CopySqlAction(console, editor));
        actionGroup.add(new ClearAllAction(console));

        return actionGroup;
    }

    private JPanel createConsolePanel(ConsoleView consoleView) {
        final JPanel panel = new JPanel(new BorderLayout());
        panel.add(consoleView.getComponent(), BorderLayout.CENTER);
        return panel;
    }

    private RunContentDescriptor getRunContentDescriptor(RunnerLayoutUi layoutUi) {
        RunContentDescriptor desc = new RunContentDescriptor(new RunProfile() {
            @Nullable
            @Override
            public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
                return null;
            }

            @NotNull
            @Override
            public String getName() {
                return "SQL";
            }

            @Override
            @Nullable
            public Icon getIcon() {
                return Icons.SQLALCHEMY;
            }
        }, new DefaultExecutionResult(), layoutUi);
        desc.setExecutionId(System.nanoTime());
        return desc;
    }

    private RunnerLayoutUi getRunnerLayoutUi() {
        return RunnerLayoutUi.Factory.getInstance(project).create("SQLAlchemy Log", "SQLAlchemy Log", "SQLAlchemy Log", project);
    }

    public void println(String logPrefix, String sql, int rgb) {
        final ConsoleViewContentType contentType = consoleViewContentTypes.computeIfAbsent(rgb, k ->
                new ConsoleViewContentType(String.valueOf(rgb), new TextAttributes(new JBColor(rgb, rgb), null, null, null, Font.PLAIN)));

        String cleanPrefix = (logPrefix != null) ? logPrefix.trim() : "";
        if (!cleanPrefix.isEmpty()) {
            consoleView.print(String.format("-- #%d -- %s\n", counter.incrementAndGet(), cleanPrefix), ConsoleViewContentType.USER_INPUT);
        } else {
            consoleView.print(String.format("-- #%d --\n", counter.incrementAndGet()), ConsoleViewContentType.USER_INPUT);
        }

        String formattedSql = isFormat() ? FORMATTER.format(sql) : sql;
        if (!formattedSql.endsWith("\n")) {
            formattedSql += "\n";
        }

        consoleView.print(formattedSql + "\n", contentType);
    }

    public boolean isFormat() {
        return PropertiesComponent.getInstance(project).getBoolean(PrettyPrintToggleAction.class.getName(), true);
    }

    public void run() {
        running = true;
    }

    public void stop() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    @Nullable
    public static SQLAlchemyLogManager getInstance(@NotNull Project project) {
        SQLAlchemyLogManager manager = project.getUserData(KEY);
        if (manager != null && Disposer.isDisposed(manager)) {
            manager = null;
        }
        return manager;
    }

    @NotNull
    public static SQLAlchemyLogManager getInstanceOrCreate(@NotNull Project project) {
        SQLAlchemyLogManager manager = getInstance(project);
        if (manager != null) {
            return manager;
        }
        synchronized (project) {
            manager = getInstance(project);
            if (manager != null) {
                return manager;
            }
            return createInstance(project);
        }
    }

    @NotNull
    public static SQLAlchemyLogManager createInstance(@NotNull Project project) {
        SQLAlchemyLogManager manager = getInstance(project);
        if (manager != null && !Disposer.isDisposed(manager)) {
            Disposer.dispose(manager);
        }

        manager = new SQLAlchemyLogManager(project);
        project.putUserData(KEY, manager);
        return manager;
    }

    @Nullable
    public ToolWindow getToolWindow() {
        return ToolWindowManager.getInstance(project).getToolWindow(SQLAlchemyLogExecutor.TOOL_WINDOW_ID);
    }

    public void resetKeywords(String text) {
        keywords.clear();
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                keywords.add(trimmed);
            }
        }
    }

    public String getEnginePrefix() {
        return enginePrefix != null ? enginePrefix : "sqlalchemy.engine";
    }

    public void setEnginePrefix(String enginePrefix) {
        this.enginePrefix = enginePrefix;
    }

    public String getParametersPrefix() {
        return parametersPrefix != null ? parametersPrefix : "[";
    }

    public void setParametersPrefix(String parametersPrefix) {
        this.parametersPrefix = parametersPrefix;
    }

    public List<String> getKeywords() {
        return Collections.unmodifiableList(keywords);
    }

    @Override
    public void dispose() {
        project.putUserData(KEY, null);
        stop();
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed()) {
                ExecutionManager.getInstance(project).getContentManager().removeRunContent(
                        SQLAlchemyLogExecutor.getInstance(),
                        descriptor
                );
            }
        }, ModalityState.any());
    }

    private static final class RangeHighlighterDocumentListener implements DocumentListener {
        private final Editor editor;

        private RangeHighlighterDocumentListener(Editor editor) {
            this.editor = editor;
        }

        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
            final Document document = event.getDocument();
            final int textLength = document.getTextLength();
            if (textLength < 1) {
                return;
            }

            for (int i = event.getOffset(); i < textLength; ) {
                final int endOffset = document.getLineEndOffset(document.getLineNumber(i));
                final String text = document.getText(TextRange.create(i, endOffset));
                if (text.matches("^-- #[\\d]+ --.*")) {
                    editor.getMarkupModel().addRangeHighlighter(
                            i,
                            i + 1,
                            JumpSqlAction.SQL_LAYER,
                            TextAttributes.ERASE_MARKER,
                            HighlighterTargetArea.EXACT_RANGE
                    );
                }
                i = endOffset + 1;
            }
        }
    }
}
