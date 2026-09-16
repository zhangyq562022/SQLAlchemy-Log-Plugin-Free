package com.github.sqlalchemylog.action;

import com.github.sqlalchemylog.Icons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import org.jetbrains.annotations.NotNull;

public class PrettyPrintToggleAction extends ToggleAction {

    public PrettyPrintToggleAction() {
        super("Pretty Print", "Format and indent SQL queries", Icons.PRETTY_PRINT);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        if (e.getProject() == null) {
            return false;
        }
        return PropertiesComponent.getInstance(e.getProject()).getBoolean(PrettyPrintToggleAction.class.getName(), true);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        if (e.getProject() == null) {
            return;
        }
        PropertiesComponent.getInstance(e.getProject()).setValue(PrettyPrintToggleAction.class.getName(), state, true);
    }
}
