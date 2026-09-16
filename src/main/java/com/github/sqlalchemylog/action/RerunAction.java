package com.github.sqlalchemylog.action;

import com.github.sqlalchemylog.gui.SQLAlchemyLogManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class RerunAction extends AnAction {

    public RerunAction() {
        super("Rerun", "Restart SQLAlchemy log capturing", AllIcons.Actions.Restart);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (e.getProject() != null) {
            new SQLAlchemyLogAction().rerun(e.getProject());
        }
    }
}
