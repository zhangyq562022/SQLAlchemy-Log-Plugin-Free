package com.github.sqlalchemylog.gui;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.Objects;

import static com.github.sqlalchemylog.SQLAlchemyLogConsoleFilter.*;

public class SettingsDialogWrapper extends DialogWrapper {

    private final Project project;
    private final SQLAlchemyLogManager manager;

    private JBTextField txtEnginePrefix;
    private JBTextField txtParametersPrefix;
    private JBTextArea txtKeywords;

    private JPanel panelSelectColor;
    private JPanel panelInsertColor;
    private JPanel panelUpdateColor;
    private JPanel panelDeleteColor;
    private JPanel panelTxColor;

    public SettingsDialogWrapper(Project project, SQLAlchemyLogManager manager) {
        super(Objects.requireNonNull(project, "project"), false);
        this.project = project;
        this.manager = manager;

        setTitle("SQLAlchemy Log Free Settings");
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JBTabbedPane tabbedPane = new JBTabbedPane();
        tabbedPane.setPreferredSize(new Dimension(540, 360));

        // Tab 1: Filter & Patterns
        JPanel filterTab = new JPanel(new BorderLayout(10, 10));
        filterTab.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel topGrid = new JPanel(new GridLayout(2, 2, 8, 8));
        topGrid.add(new JLabel("Engine Log Prefix:"));
        txtEnginePrefix = new JBTextField(manager.getEnginePrefix());
        txtEnginePrefix.setToolTipText("Matches lines from the SQLAlchemy engine logger (e.g., sqlalchemy.engine)");
        topGrid.add(txtEnginePrefix);

        topGrid.add(new JLabel("Parameter Indicator:"));
        txtParametersPrefix = new JBTextField(manager.getParametersPrefix());
        txtParametersPrefix.setToolTipText("Indicator prefix for the parameter line (e.g., [ or [generated in)");
        topGrid.add(txtParametersPrefix);

        filterTab.add(topGrid, BorderLayout.NORTH);

        JPanel keywordsPanel = new JPanel(new BorderLayout(4, 4));
        keywordsPanel.setBorder(new TitledBorder(new LineBorder(JBColor.border()), "Ignore SQL Containing Keywords (One per line)"));
        txtKeywords = new JBTextArea(String.join("\n", manager.getKeywords()));
        txtKeywords.setRows(6);
        keywordsPanel.add(new JBScrollPane(txtKeywords), BorderLayout.CENTER);

        filterTab.add(keywordsPanel, BorderLayout.CENTER);
        tabbedPane.addTab("Filters & Matching", filterTab);

        // Tab 2: Colors
        JPanel colorTab = new JPanel(new GridLayout(5, 1, 8, 8));
        colorTab.setBorder(new EmptyBorder(16, 20, 16, 20));

        final PropertiesComponent props = PropertiesComponent.getInstance(project);
        int defaultErrColor = ConsoleViewContentType.ERROR_OUTPUT.getAttributes().getForegroundColor().getRGB();

        panelSelectColor = createColorRow(colorTab, "SELECT Query Color:", props.getInt(SELECT_SQL_COLOR_KEY, new JBColor(0x0066CC, 0x589DF6).getRGB()));
        panelInsertColor = createColorRow(colorTab, "INSERT Query Color:", props.getInt(INSERT_SQL_COLOR_KEY, new JBColor(0x2E7D32, 0x499C54).getRGB()));
        panelUpdateColor = createColorRow(colorTab, "UPDATE Query Color:", props.getInt(UPDATE_SQL_COLOR_KEY, new JBColor(0xE65100, 0xCC7832).getRGB()));
        panelDeleteColor = createColorRow(colorTab, "DELETE Query Color:", props.getInt(DELETE_SQL_COLOR_KEY, new JBColor(0xC62828, 0xBC3F3C).getRGB()));
        panelTxColor = createColorRow(colorTab, "TRANSACTION (BEGIN/COMMIT):", props.getInt(TX_SQL_COLOR_KEY, new JBColor(0x616161, 0x808080).getRGB()));

        tabbedPane.addTab("SQL Colors", colorTab);

        return tabbedPane;
    }

    private JPanel createColorRow(JPanel parent, String labelText, int initialRGB) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.add(new JLabel(labelText), BorderLayout.WEST);

        JPanel colorBox = new JPanel();
        colorBox.setPreferredSize(new Dimension(50, 22));
        colorBox.setBackground(new JBColor(initialRGB, initialRGB));
        colorBox.setBorder(new LineBorder(JBColor.border(), 1));
        colorBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        colorBox.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Color chosen = JColorChooser.showDialog(
                        getContentPane(),
                        "Choose " + labelText,
                        colorBox.getBackground()
                );
                if (chosen != null) {
                    colorBox.setBackground(chosen);
                }
            }
        });

        row.add(colorBox, BorderLayout.EAST);
        parent.add(row);
        return colorBox;
    }

    @Override
    protected void doOKAction() {
        saveSettings();
        super.doOKAction();
    }

    private void saveSettings() {
        final PropertiesComponent props = PropertiesComponent.getInstance(project);

        String enginePrefix = txtEnginePrefix.getText().trim();
        if (enginePrefix.isEmpty()) {
            enginePrefix = "sqlalchemy.engine";
        }

        String paramPrefix = txtParametersPrefix.getText().trim();
        if (paramPrefix.isEmpty()) {
            paramPrefix = "[";
        }

        String keywords = txtKeywords.getText();

        props.setValue(ENGINE_PREFIX_KEY, enginePrefix);
        props.setValue(PARAMETERS_PREFIX_KEY, paramPrefix);
        props.setValue(KEYWORDS_KEY, keywords);

        int selectRGB = panelSelectColor.getBackground().getRGB();
        int insertRGB = panelInsertColor.getBackground().getRGB();
        int updateRGB = panelUpdateColor.getBackground().getRGB();
        int deleteRGB = panelDeleteColor.getBackground().getRGB();
        int txRGB = panelTxColor.getBackground().getRGB();

        props.setValue(SELECT_SQL_COLOR_KEY, selectRGB, 0);
        props.setValue(INSERT_SQL_COLOR_KEY, insertRGB, 0);
        props.setValue(UPDATE_SQL_COLOR_KEY, updateRGB, 0);
        props.setValue(DELETE_SQL_COLOR_KEY, deleteRGB, 0);
        props.setValue(TX_SQL_COLOR_KEY, txRGB, 0);

        manager.setEnginePrefix(enginePrefix);
        manager.setParametersPrefix(paramPrefix);
        manager.resetKeywords(keywords);
    }
}
