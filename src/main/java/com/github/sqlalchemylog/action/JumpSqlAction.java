package com.github.sqlalchemylog.action;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class JumpSqlAction extends AnAction {

    public static final int SQL_LAYER = 506;

    protected final ConsoleView consoleView;
    protected final Editor editor;

    public JumpSqlAction(@Nullable String text, @Nullable String description, @Nullable Icon icon, ConsoleView consoleView, Editor editor) {
        super(text, description, icon);
        this.consoleView = consoleView;
        this.editor = editor;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (editor != null && e.getInputEvent() != null && !e.getInputEvent().isShiftDown()) {
            editor.getSelectionModel().removeSelection();
        }
    }

    protected boolean isValid(RangeHighlighter next) {
        return next != null && next.isValid() && next.getLayer() == SQL_LAYER;
    }

    protected void moveTo(int offset) {
        if (editor == null) return;
        editor.getCaretModel().getPrimaryCaret().moveToOffset(offset);
        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() ->
                IdeFocusManager.getGlobalInstance().requestFocus(editor.getContentComponent(), true));
    }
}
