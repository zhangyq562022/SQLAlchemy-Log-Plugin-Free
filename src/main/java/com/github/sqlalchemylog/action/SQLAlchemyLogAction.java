package com.github.sqlalchemylog.action;

import com.github.sqlalchemylog.Icons;
import com.github.sqlalchemylog.gui.SQLAlchemyLogManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import org.jetbrains.annotations.NotNull;

public class SQLAlchemyLogAction extends DumbAwareAction {

    public SQLAlchemyLogAction() {
        super("SQLAlchemy Log", "Open SQLAlchemy Log viewer", Icons.SQLALCHEMY);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getProject();
        if (project == null || !project.isOpen() || !project.isInitialized()) {
            return;
        }

        if ("EditorPopup".equals(e.getPlace()) || "ConsoleEditorPopupMenu".equals(e.getPlace())) {
            SQLAlchemyLogManager manager = SQLAlchemyLogManager.getInstance(project);
            if (manager != null) {
                ToolWindow tw = manager.getToolWindow();
                if (tw != null && tw.isAvailable()) {
                    if (!manager.isRunning()) {
                        manager.run();
                    }
                    tw.activate(null);
                    return;
                }
            }
        }

        rerun(project);
    }

    public void rerun(final Project project) {
        SQLAlchemyLogManager manager = SQLAlchemyLogManager.getInstance(project);
        if (manager != null) {
            Disposer.dispose(manager);
        }
        SQLAlchemyLogManager.createInstance(project).run();
    }
}
