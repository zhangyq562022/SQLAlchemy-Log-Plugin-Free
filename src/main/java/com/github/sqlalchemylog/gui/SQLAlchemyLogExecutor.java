package com.github.sqlalchemylog.gui;

import com.github.sqlalchemylog.Icons;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SQLAlchemyLogExecutor extends Executor {

    public static final String TOOL_WINDOW_ID = "SQLAlchemy Log";

    @Override
    public @NotNull String getToolWindowId() {
        return TOOL_WINDOW_ID;
    }

    @Override
    public @NotNull Icon getToolWindowIcon() {
        return Icons.SQLALCHEMY;
    }

    @Override
    public @NotNull Icon getIcon() {
        return Icons.SQLALCHEMY;
    }

    @Override
    public Icon getDisabledIcon() {
        return Icons.SQLALCHEMY;
    }

    @Override
    public String getDescription() {
        return "SQLAlchemy Log";
    }

    @NotNull
    @Override
    public String getActionName() {
        return getDescription();
    }

    @NotNull
    @Override
    public String getId() {
        return TOOL_WINDOW_ID;
    }

    @NotNull
    @Override
    public String getStartActionText() {
        return getDescription();
    }

    @Override
    public String getContextActionId() {
        return getDescription();
    }

    @Override
    public String getHelpId() {
        return TOOL_WINDOW_ID;
    }

    public static Executor getInstance() {
        return ExecutorRegistry.getInstance().getExecutorById(TOOL_WINDOW_ID);
    }
}
