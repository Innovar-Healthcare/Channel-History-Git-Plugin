package com.innovarhealthcare.channelHistory.client.diff;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;
import java.text.SimpleDateFormat;

import com.innovarhealthcare.channelHistory.client.diff.model.DiffResult;
import com.innovarhealthcare.channelHistory.client.diff.model.ScriptDiffEngine;
import com.innovarhealthcare.channelHistory.client.diff.model.VersionInfo;

class DiffComparisonPanel extends JPanel {
    private static final Color HEADER_BG = new Color(250, 251, 252);
    private static final Color BORDER_COLOR = new Color(224, 224, 224);
    private static final Font HEADER_FONT = new Font("SansSerif", Font.PLAIN, 12);
    private static final Font DATE_FONT = new Font("SansSerif", Font.PLAIN, 11);

    private DiffTextPane leftPane;
    private DiffTextPane rightPane;
    private JScrollPane leftScroll;
    private JScrollPane rightScroll;
    private VersionInfo leftVersion;
    private VersionInfo rightVersion;

    public DiffComparisonPanel(String leftContent, String rightContent, VersionInfo leftVersion, VersionInfo rightVersion) {
        this.leftVersion = leftVersion;
        this.rightVersion = rightVersion;

        setLayout(new GridLayout(1, 2, 1, 0));

        leftPane = new DiffTextPane();
        rightPane = new DiffTextPane();

        // Create panels with headers
        JPanel leftPanel = createPanelWithHeader(leftPane, leftVersion, true);
        JPanel rightPanel = createPanelWithHeader(rightPane, rightVersion, false);

        add(leftPanel);
        add(rightPanel);

        updateDiff(leftContent, rightContent);
    }

    private JPanel createPanelWithHeader(DiffTextPane textPane, VersionInfo version, boolean isLeft) {
        JPanel panel = new JPanel(new BorderLayout());

        // Header
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(HEADER_BG);
        header.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR), BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        // Line 1: Version/Current • Author
        JLabel line1 = new JLabel(formatVersionLine(version));
        line1.setFont(HEADER_FONT);
        line1.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Line 2: Timestamp
        JLabel line2 = new JLabel(formatDateLine(version));
        line2.setFont(DATE_FONT);
        line2.setForeground(new Color(117, 117, 117));
        line2.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(line1);
        header.add(Box.createVerticalStrut(4));
        header.add(line2);

        // Content
        JScrollPane scrollPane = new JScrollPane(textPane);
        if (isLeft) {
            leftScroll = scrollPane;
        } else {
            rightScroll = scrollPane;
        }

        panel.add(header, BorderLayout.NORTH);
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
            sb.append("Version ").append(version.getVersion());
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

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm");

        return sdf.format(version.getTimestamp());
    }

    public void updateDiff(String leftContent, String rightContent) {
        try {
            DiffResult diff = ScriptDiffEngine.computeDiff(leftContent, rightContent);

            leftPane.setDiffLines(diff.getLeftLines());
            rightPane.setDiffLines(diff.getRightLines());

            // Scroll to top
            SwingUtilities.invokeLater(() -> {
                leftScroll.getVerticalScrollBar().setValue(0);
                rightScroll.getVerticalScrollBar().setValue(0);
            });

        } catch (Exception e) {
            e.printStackTrace();
            leftPane.setText("Error computing diff: " + e.getMessage());
            rightPane.setText("");
        }
    }

    private void setupSynchronizedScrolling() {
        JScrollBar leftVBar = leftScroll.getVerticalScrollBar();
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

