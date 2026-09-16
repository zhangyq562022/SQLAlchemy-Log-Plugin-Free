package com.github.sqlalchemylog.action;

import com.github.sqlalchemylog.gui.SQLAlchemyLogManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class StopAction extends AnAction {

    private final SQLAlchemyLogManager manager;

    public StopAction(SQLAlchemyLogManager manager) {
        super("Stop", "Pause SQLAlchemy log capturing", AllIcons.Actions.Suspend);
        this.manager = manager;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        manager.stop();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(manager.isRunning());
    }
}
