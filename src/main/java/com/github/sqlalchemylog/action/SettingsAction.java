package com.github.sqlalchemylog.action;

import com.github.sqlalchemylog.gui.SQLAlchemyLogManager;
import com.github.sqlalchemylog.gui.SettingsDialogWrapper;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class SettingsAction extends AnAction {
    private final SQLAlchemyLogManager manager;

    public SettingsAction(SQLAlchemyLogManager manager) {
        super("Settings", "Configure SQLAlchemy Log settings", AllIcons.General.GearPlain);
        this.manager = manager;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (e.getProject() != null) {
            new SettingsDialogWrapper(e.getProject(), manager).showAndGet();
        }
    }
}
