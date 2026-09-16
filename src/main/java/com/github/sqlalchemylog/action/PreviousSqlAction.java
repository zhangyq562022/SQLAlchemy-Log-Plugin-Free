package com.github.sqlalchemylog.action;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import org.jetbrains.annotations.NotNull;

public class PreviousSqlAction extends JumpSqlAction {

    public PreviousSqlAction(ConsoleView consoleView, Editor editor) {
        super("Previous SQL", "Navigate to previous SQL statement", AllIcons.Actions.PreviousOccurence, consoleView, editor);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        super.actionPerformed(e);
        if (editor == null) return;

        final int offset = editor.getCaretModel().getPrimaryCaret().getOffset();
        if (offset <= 1) {
            return;
        }

        RangeHighlighter target = findPreviousHighlighter(offset);
        if (target != null) {
            int movedOffset = target.getStartOffset();
            moveTo(movedOffset);
            if (e.getInputEvent() != null && e.getInputEvent().isShiftDown()) {
                editor.getSelectionModel().setSelection(offset, movedOffset);
            }
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(hasPrev());
    }

    private RangeHighlighter findPreviousHighlighter(int currentOffset) {
        if (editor == null) return null;
        MarkupModel model = editor.getMarkupModel();
        RangeHighlighter[] highlighters = model.getAllHighlighters();
        RangeHighlighter best = null;
        int bestStart = -1;
        int currentLine = editor.getDocument().getLineNumber(Math.max(0, currentOffset - 1));

        for (RangeHighlighter rh : highlighters) {
            if (isValid(rh) && rh.getStartOffset() < currentOffset) {
                int line = editor.getDocument().getLineNumber(rh.getStartOffset());
                if (line != currentLine && rh.getStartOffset() > bestStart) {
                    best = rh;
                    bestStart = rh.getStartOffset();
                }
            }
        }
        return best;
    }

    private boolean hasPrev() {
        if (editor == null) return false;
        int offset = editor.getCaretModel().getPrimaryCaret().getOffset();
        return offset > 1 && findPreviousHighlighter(offset) != null;
    }
}
