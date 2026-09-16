package com.github.sqlalchemylog.action;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import org.jetbrains.annotations.NotNull;

public class NextSqlAction extends JumpSqlAction {

    public NextSqlAction(ConsoleView consoleView, Editor editor) {
        super("Next SQL", "Navigate to next SQL statement", AllIcons.Actions.NextOccurence, consoleView, editor);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        super.actionPerformed(e);
        if (editor == null) return;

        final int offset = editor.getCaretModel().getPrimaryCaret().getOffset() + 2;
        final int textLength = editor.getDocument().getTextLength();

        if (offset >= textLength) {
            return;
        }

        RangeHighlighter target = findNextHighlighter(offset);
        if (target != null) {
            int movedOffset = target.getStartOffset();
            moveTo(movedOffset);
            if (e.getInputEvent() != null && e.getInputEvent().isShiftDown()) {
                editor.getSelectionModel().setSelection(offset - 2, movedOffset);
            }
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(hasNext());
    }

    private RangeHighlighter findNextHighlighter(int currentOffset) {
        if (editor == null) return null;
        MarkupModel model = editor.getMarkupModel();
        RangeHighlighter[] highlighters = model.getAllHighlighters();
        RangeHighlighter best = null;
        int bestStart = Integer.MAX_VALUE;
        int currentLine = editor.getDocument().getLineNumber(Math.min(editor.getDocument().getTextLength(), Math.max(0, currentOffset - 2)));

        for (RangeHighlighter rh : highlighters) {
            if (isValid(rh) && rh.getStartOffset() >= currentOffset) {
                int line = editor.getDocument().getLineNumber(rh.getStartOffset());
                if (line != currentLine && rh.getStartOffset() < bestStart) {
                    best = rh;
                    bestStart = rh.getStartOffset();
                }
            }
        }
        return best;
    }

    private boolean hasNext() {
        if (editor == null) return false;
        int offset = editor.getCaretModel().getPrimaryCaret().getOffset() + 2;
        return offset < editor.getDocument().getTextLength() && findNextHighlighter(offset) != null;
    }
}
