package com.innovarhealthcare.channelHistory.client.diff;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.text.SimpleDateFormat;
import java.util.List;

import com.innovarhealthcare.channelHistory.client.diff.model.DiffLine;
import com.innovarhealthcare.channelHistory.client.diff.model.DiffResult;
import com.innovarhealthcare.channelHistory.client.diff.model.ScriptDiffEngine;
import com.innovarhealthcare.channelHistory.client.diff.model.VersionInfo;

public class DiffComparisonPanel extends JPanel {

    private static final Color HEADER_BG    = new Color(250, 251, 252);
    private static final Color BORDER_COLOR = new Color(224, 224, 224);
    private static final Font  HEADER_FONT  = new Font("SansSerif", Font.PLAIN, 12);
    private static final Font  DATE_FONT    = new Font("SansSerif", Font.PLAIN, 11);

    private static final String CARD_SPLIT   = "SPLIT";
    private static final String CARD_UNIFIED = "UNIFIED";

    // ── Split mode ────────────────────────────────────────────────────────────
    private final DiffTextPane  leftPane;
    private final DiffTextPane  rightPane;
    private       JScrollPane   leftScroll;
    private       JScrollPane   rightScroll;

    // ── Unified mode ──────────────────────────────────────────────────────────
    private final DiffTextPane  unifiedPane;
    private final JScrollPane   unifiedScroll;

    // ── Layout ────────────────────────────────────────────────────────────────
    private final CardLayout    cardLayout;
    private final JPanel        contentArea;
    private final JButton       toggleButton;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean isSplitMode     = true;
    private String  currentLeftText  = "";
    private String  currentRightText = "";

    public DiffComparisonPanel(VersionInfo leftVersion, VersionInfo rightVersion) {
        setLayout(new BorderLayout());

        leftPane    = new DiffTextPane();
        rightPane   = new DiffTextPane();
        unifiedPane = new DiffTextPane();

        // ── Toolbar ───────────────────────────────────────────────────────────
        toggleButton = new JButton("Unified");
        toggleButton.addActionListener(e -> toggleMode());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));
        toolbar.add(toggleButton);

        // ── Split content ─────────────────────────────────────────────────────
        JPanel splitContent = new JPanel(new GridLayout(1, 2, 1, 0));
        splitContent.add(createPanelWithHeader(leftPane, leftVersion, true));
        splitContent.add(createPanelWithHeader(rightPane, rightVersion, false));

        // ── Unified content ───────────────────────────────────────────────────
        unifiedScroll = new JScrollPane(unifiedPane);
        JPanel unifiedContent = new JPanel(new BorderLayout());
        unifiedContent.add(unifiedScroll, BorderLayout.CENTER);

        // ── Card area ─────────────────────────────────────────────────────────
        cardLayout  = new CardLayout();
        contentArea = new JPanel(cardLayout);
        contentArea.add(splitContent,   CARD_SPLIT);
        contentArea.add(unifiedContent, CARD_UNIFIED);

        add(toolbar,     BorderLayout.NORTH);
        add(contentArea, BorderLayout.CENTER);

        setupSynchronizedScrolling();
    }

    // ── Toggle ────────────────────────────────────────────────────────────────

    private void toggleMode() {
        isSplitMode = !isSplitMode;
        cardLayout.show(contentArea, isSplitMode ? CARD_SPLIT : CARD_UNIFIED);
        toggleButton.setText(isSplitMode ? "Unified" : "Split");
    }

    // ── Diff rendering ────────────────────────────────────────────────────────

    public void updateDiff(String leftContent, String rightContent) {
        currentLeftText  = leftContent  != null ? leftContent  : "";
        currentRightText = rightContent != null ? rightContent : "";

        try {
            // Split diff
            DiffResult diff = ScriptDiffEngine.computeDiff(currentLeftText, currentRightText);
            leftPane.setDiffLines(diff.getLeftLines());
            rightPane.setDiffLines(diff.getRightLines());

            // Unified diff
            List<DiffLine> unified = ScriptDiffEngine.computeUnifiedDiff(currentLeftText, currentRightText);
            unifiedPane.setDiffLines(unified);

            // Scroll all to top
            SwingUtilities.invokeLater(() -> {
                leftScroll.getVerticalScrollBar().setValue(0);
                rightScroll.getVerticalScrollBar().setValue(0);
                unifiedScroll.getVerticalScrollBar().setValue(0);
            });

        } catch (Exception e) {
            e.printStackTrace();
            leftPane.setText("Error computing diff: " + e.getMessage());
            rightPane.setText("");
            unifiedPane.setText("Error computing diff: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JPanel createPanelWithHeader(DiffTextPane textPane, VersionInfo version, boolean isLeft) {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(HEADER_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        JLabel line1 = new JLabel(formatVersionLine(version));
        line1.setFont(HEADER_FONT);
        line1.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel line2 = new JLabel(formatDateLine(version));
        line2.setFont(DATE_FONT);
        line2.setForeground(new Color(117, 117, 117));
        line2.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(line1);
        header.add(Box.createVerticalStrut(4));
        header.add(line2);

        JScrollPane scrollPane = new JScrollPane(textPane);
        if (isLeft) {
            leftScroll = scrollPane;
        } else {
            rightScroll = scrollPane;
        }

        panel.add(header,     BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private String formatVersionLine(VersionInfo version) {
        if (version == null) {
            return "No version info";
        }

        StringBuilder sb = new StringBuilder();

        if (version.isCurrent()) {
            sb.append("🟢 CURRENT");
        } else {
            sb.append(version.getVersion());
        }

        if (version.getAuthor() != null && !version.getAuthor().isEmpty()) {
            sb.append(" • ").append(version.getAuthor());
        }

        return sb.toString();
    }

    private String formatDateLine(VersionInfo version) {
        if (version == null || version.getTimestamp() == null) {
            return "";
        }

        return new SimpleDateFormat("MMM dd, yyyy HH:mm").format(version.getTimestamp());
    }

    private void setupSynchronizedScrolling() {
        JScrollBar leftVBar  = leftScroll.getVerticalScrollBar();
        JScrollBar rightVBar = rightScroll.getVerticalScrollBar();

        leftVBar.addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting()) {
                rightVBar.setValue(e.getValue());
            }
        });

        rightVBar.addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting()) {
                leftVBar.setValue(e.getValue());
            }
        });
    }
}
