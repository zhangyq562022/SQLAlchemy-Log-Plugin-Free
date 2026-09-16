package com.github.sqlalchemylog.action;

import com.github.sqlalchemylog.Icons;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

public class CopySqlAction extends AnAction {
    private final ConsoleView consoleView;
    private final Editor editor;

    public CopySqlAction(ConsoleView consoleView, Editor editor) {
        super("Copy SQL", "Copy selected SQL or console text", Icons.COPY);
        this.consoleView = consoleView;
        this.editor = editor;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (editor == null) return;

        String selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText != null && !selectedText.isEmpty()) {
            CopyPasteManager.getInstance().setContents(new StringSelection(selectedText));
        } else {
            String fullText = editor.getDocument().getText();
            if (!fullText.isEmpty()) {
                CopyPasteManager.getInstance().setContents(new StringSelection(fullText));
            }
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean enabled = consoleView != null && consoleView.getContentSize() > 0;
        e.getPresentation().setEnabled(enabled);
    }
}
