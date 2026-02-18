package com.innovarhealthcare.channelHistory.client.dialog;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Font;
import java.awt.Window;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Objects;

import com.innovarhealthcare.channelHistory.shared.ObjectDiff;
import com.kayyagari.objmeld.OgnlComparison;
import com.kayyagari.objmeld.StringContent;
import com.mirth.connect.client.ui.ChannelSetup;
import com.mirth.connect.client.ui.MirthDialog;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.model.Channel;
import net.miginfocom.swing.MigLayout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Dialog for comparing two versions of an object side-by-side.
 * Provides multiple views: XML comparison, object diff, and optional channel visualization.
 */
public class VersionComparisonDialog extends MirthDialog {
    private static final Logger logger = LogManager.getLogger(VersionComparisonDialog.class);

    // Dialog constants
    private static final String TAB_XML_VIEW = "XML View";
    private static final String TAB_OBJECT_VIEW = "Object View";
    private static final String TAB_CHANNEL_VIEW = "Channel View";

    private static final Color SEPARATOR_COLOR = new Color(224, 224, 224);
    private static final int PANEL_PADDING = 8;

    // ==================== FIELDS ====================

    private VersionInfo leftVersion;
    private VersionInfo rightVersion;
    private Object leftObject;
    private Object rightObject;

    private JTabbedPane tabbedPane;
    private JButton closeButton;

    private Window parentWindow;

    // ==================== CONSTRUCTOR ====================

    /**
     * Private constructor. Use factory method create() instead.
     */
    private VersionComparisonDialog(String title, VersionInfo leftVersion, VersionInfo rightVersion, Object leftObject, Object rightObject, Window parent) {
        super(parent, true);

        this.parentWindow = parent;
        this.leftVersion = leftVersion;
        this.rightVersion = rightVersion;
        this.leftObject = leftObject;
        this.rightObject = rightObject;

        initComponents();
        initLayout();

        setTitle(title);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }

    // ==================== PUBLIC API ====================

    /**
     * Factory method to create and display a comparison dialog.
     *
     * @param title        Dialog title
     * @param leftVersion  Left version information
     * @param rightVersion Right version information
     * @param leftObject   Left object to compare
     * @param rightObject  Right object to compare
     * @param leftContent  Left content as XML string
     * @param rightContent Right content as XML string
     * @param parent       Parent window
     * @return Initialized and visible dialog
     */
    public static VersionComparisonDialog create(String title, VersionInfo leftVersion, VersionInfo rightVersion, Object leftObject, Object rightObject, String leftContent, String rightContent, Window parent) {
        VersionComparisonDialog dialog = new VersionComparisonDialog(title, leftVersion, rightVersion, leftObject, rightObject, parent);

        // Prepare all views with content
        dialog.prepareTextView(leftContent, rightContent);
        dialog.prepareObjectView();

        // Finalize and show
        dialog.finalizeAndShow();

        return dialog;
    }

    /**
     * Override setSize to allow custom sizing after creation.
     */
    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
    }

    // ==================== INIT METHODS ====================

    /**
     * Initialize UI components.
     */
    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);
        getContentPane().setBackground(getBackground());

        // Tabbed pane for different views
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setBackground(UIConstants.BACKGROUND_COLOR);

        // Close button
        closeButton = new JButton("Close");
        closeButton.addActionListener(evt -> dispose());
    }

    /**
     * Initialize layout structure.
     */
    private void initLayout() {
        setLayout(new MigLayout("insets 12, fill", "[grow,fill]", "[grow,fill][]"));

        add(tabbedPane, "grow, push, wrap");
        add(new JSeparator(), "growx, wrap");
        add(closeButton, "tag ok, right");
    }

    // ==================== VIEW PREPARATION ====================

    /**
     * Prepare XML/Text comparison view.
     */
    private void prepareTextView(String leftContent, String rightContent) {
        try {
            JPanel tabContent = new JPanel(new MigLayout("insets 12, fill", "[grow,fill]", "[][grow,fill]"));
            tabContent.setBackground(UIConstants.BACKGROUND_COLOR);

            // Header with version labels
            JPanel labelPanel = createLabelPanel();

            // Comparison panel
            JPanel comparisonPanel = OgnlComparison.prepare(Collections.singletonList(new StringContent("", leftContent)), Collections.singletonList(new StringContent("", rightContent)), true);

            tabContent.add(labelPanel, "wrap");
            tabContent.add(comparisonPanel, "grow, push");

            tabbedPane.addTab(TAB_XML_VIEW, tabContent);

        } catch (Exception e) {
            logger.error("Failed to prepare text view", e);
            tabbedPane.addTab(TAB_XML_VIEW, createErrorPanel("Text View", e));
        }
    }

    /**
     * Prepare object difference visualization view.
     */
    private void prepareObjectView() {
        try {
            JPanel tabContent = new JPanel(new MigLayout("insets 12, fill", "[grow,fill]", "[][grow,fill]"));
            tabContent.setBackground(UIConstants.BACKGROUND_COLOR);

            // Header with version labels
            JPanel labelPanel = createLabelPanel();

            // Object diff panel
            ObjectDiff objectDiff = new ObjectDiff(leftObject, rightObject);
            objectDiff.create();
            JPanel diffPanel = objectDiff.getVisualPanel();

            tabContent.add(labelPanel, "wrap");
            tabContent.add(new JScrollPane(diffPanel), "grow, push");

            tabbedPane.addTab(TAB_OBJECT_VIEW, tabContent);

        } catch (Exception e) {
            logger.error("Failed to prepare object view", e);
            tabbedPane.addTab(TAB_OBJECT_VIEW, createErrorPanel("Object View", e));
        }
    }

    /**
     * Prepare channel visualization view (only for Channel objects).
     */
    private void prepareChannelView() {
        if (!(leftObject instanceof Channel && rightObject instanceof Channel)) {
            logger.debug("Skipping channel view - objects are not channels");
            return;
        }

        try {
            JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
            splitPane.setDividerLocation(0.5);
            splitPane.setResizeWeight(0.5);

            // Left channel setup
            ChannelSetup leftChannelSetup = new ChannelSetup();
            leftChannelSetup.addChannel((Channel) leftObject, "");
            splitPane.setLeftComponent(leftChannelSetup);

            // Right channel setup
            ChannelSetup rightChannelSetup = new ChannelSetup();
            rightChannelSetup.addChannel((Channel) rightObject, "");
            splitPane.setRightComponent(rightChannelSetup);

            tabbedPane.addTab(TAB_CHANNEL_VIEW, splitPane);

        } catch (Exception e) {
            logger.error("Failed to prepare channel view", e);
            tabbedPane.addTab(TAB_CHANNEL_VIEW, createErrorPanel("Channel View", e));
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Create header panel with left and right version information.
     */
    private JPanel createLabelPanel() {
        JPanel panel = new JPanel(new MigLayout("insets " + PANEL_PADDING + ", fill, gap 20", "[grow,center][grow,center]",  // ← Use "center" instead of "fill"
                "[]"));
        panel.setBackground(UIConstants.BACKGROUND_COLOR);
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, SEPARATOR_COLOR));

        VersionInfoPanel leftPanel = new VersionInfoPanel(leftVersion);
        VersionInfoPanel rightPanel = new VersionInfoPanel(rightVersion);

        panel.add(leftPanel);
        panel.add(rightPanel);

        return panel;
    }

    /**
     * Create error panel when a view fails to load.
     */
    private JPanel createErrorPanel(String viewName, Exception exception) {
        JPanel panel = new JPanel(new MigLayout("insets 12, fill"));
        panel.setBackground(UIConstants.BACKGROUND_COLOR);

        JLabel errorLabel = new JLabel("Failed to load " + viewName);
        errorLabel.setFont(errorLabel.getFont().deriveFont(Font.BOLD));
        errorLabel.setForeground(new Color(211, 47, 47)); // Red

        JTextArea errorArea = new JTextArea();
        errorArea.setEditable(false);
        errorArea.setLineWrap(true);
        errorArea.setWrapStyleWord(true);
        errorArea.setText(getStackTraceString(exception));
        errorArea.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(errorArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        panel.add(errorLabel, "wrap");
        panel.add(scrollPane, "grow, push");

        return panel;
    }

    /**
     * Convert exception to string with stack trace.
     */
    private String getStackTraceString(Exception exception) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        exception.printStackTrace(printWriter);
        return stringWriter.toString();
    }

    /**
     * Finalize dialog setup and display.
     */
    private void finalizeAndShow() {
        setSize(1200, 800);
        setLocationRelativeTo(parentWindow);
        setVisible(true);
    }

    // ==================== INNER CLASSES ====================

    /**
     * Immutable model representing version information for comparison.
     */
    public static class VersionInfo {
        private final String name;
        private final String version;
        private final String author;
        private final Date timestamp;
        private final boolean isCurrent;

        private VersionInfo(Builder builder) {
            this.name = Objects.requireNonNull(builder.name, "Name cannot be null");
            this.version = Objects.requireNonNull(builder.version, "Version cannot be null");
            this.author = builder.author;
            this.timestamp = builder.timestamp;
            this.isCurrent = builder.isCurrent;
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }

        public String getAuthor() {
            return author;
        }

        public Date getTimestamp() {
            return timestamp;
        }

        public boolean isCurrent() {
            return isCurrent;
        }

        /**
         * Builder for flexible VersionInfo construction
         */
        public static class Builder {
            private String name;
            private String version;
            private String author;
            private Date timestamp;
            private boolean isCurrent = false;

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder version(String version) {
                this.version = version;
                return this;
            }

            public Builder author(String author) {
                this.author = author;
                return this;
            }

            public Builder timestamp(Date timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public Builder isCurrent(boolean isCurrent) {
                this.isCurrent = isCurrent;
                return this;
            }

            public VersionInfo build() {
                return new VersionInfo(this);
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        /**
         * Helper factory method: Create current version info
         *
         * @param name   Version name (e.g., channel name)
         * @param author Author/editor name
         * @return VersionInfo marked as current
         */
        public static VersionInfo createCurrent(String name, String author) {
            return builder().name(name).version("Current").author(author).isCurrent(true).build();
        }

        /**
         * Helper factory method: Create historical version info
         *
         * @param name        Version name
         * @param versionHash Version identifier (e.g., commit hash)
         * @param author      Author who created this version
         * @param timestamp   When this version was created
         * @return VersionInfo marked as historical
         */
        public static VersionInfo createHistorical(String name, String versionHash, String author, Date timestamp) {
            return builder().name(name).version(versionHash).author(author).timestamp(timestamp).isCurrent(false).build();
        }

        @Override
        public String toString() {
            return String.format("VersionInfo{name='%s', version='%s', author='%s', isCurrent=%s}", name, version, author, isCurrent);
        }
    }

    /**
     * Panel component for displaying version information with visual styling.
     */
    private static class VersionInfoPanel extends JPanel {
        // Visual constants
        private static final Color CURRENT_INDICATOR = new Color(76, 175, 80);    // Green
        private static final Color HISTORICAL_INDICATOR = new Color(33, 150, 243); // Blue

        private static final Color NAME_COLOR = new Color(33, 33, 33);
        private static final Color AUTHOR_COLOR = new Color(117, 117, 117);
        private static final Color TIMESTAMP_COLOR = new Color(158, 158, 158);
        private static final Color BADGE_BG = new Color(238, 238, 238);
        private static final Color BADGE_TEXT = new Color(66, 66, 66);

        private static final int NAME_FONT_SIZE = 13;
        private static final int VERSION_FONT_SIZE = 11;
        private static final int METADATA_FONT_SIZE = 10;
        private static final int NAME_FONT_STYLE = Font.BOLD;

        private static final int COMPONENT_GAP = 4;
        private static final int LINE_GAP = 2;
        private static final int BADGE_PADDING_H = 6;
        private static final int BADGE_PADDING_V = 2;

        private static final String INDICATOR_SYMBOL = "●";
        private static final int INDICATOR_SIZE = 8;

        private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy HH:mm");

        private final VersionInfo versionInfo;

        public VersionInfoPanel(VersionInfo versionInfo) {
            this.versionInfo = versionInfo;
            initComponents();
        }

        private void initComponents() {
            setLayout(new MigLayout("insets 0, gap " + COMPONENT_GAP, "[center]",  // ← Column centered
                    "[]" + LINE_GAP + "[]" + LINE_GAP + "[]" + LINE_GAP + "[]"));
            setBackground(UIConstants.BACKGROUND_COLOR);

            add(createNameLabel(), "center, wrap");
            add(createVersionLabel(), "center, wrap");

            if (versionInfo.getAuthor() != null) {
                add(createAuthorLabel(), "center, wrap");
            }

            if (versionInfo.getTimestamp() != null) {
                add(createTimestampLabel(), "center");
            }
        }

        /**
         * Create name panel with type indicator
         */
        private JLabel createNameLabel() {
            JLabel label = new JLabel(versionInfo.getName());
            label.setFont(label.getFont().deriveFont(NAME_FONT_STYLE, (float) NAME_FONT_SIZE));
            label.setForeground(NAME_COLOR);
            return label;
        }

        /**
         * Create version badge label
         */
        private JLabel createVersionLabel() {
            JLabel label = new JLabel(versionInfo.getVersion());
            label.setFont(label.getFont().deriveFont(Font.PLAIN, (float) VERSION_FONT_SIZE));
            label.setForeground(BADGE_TEXT);
            label.setOpaque(true);
            label.setBackground(BADGE_BG);
            label.setBorder(BorderFactory.createEmptyBorder(BADGE_PADDING_V, BADGE_PADDING_H, BADGE_PADDING_V, BADGE_PADDING_H));
            return label;
        }

        /**
         * Create author label with icon
         */
        private JLabel createAuthorLabel() {
            JLabel label = new JLabel("👤 " + versionInfo.getAuthor());
            label.setFont(label.getFont().deriveFont(Font.PLAIN, (float) METADATA_FONT_SIZE));
            label.setForeground(AUTHOR_COLOR);
            return label;
        }

        /**
         * Create timestamp label with icon
         */
        private JLabel createTimestampLabel() {
            String formattedTime = DATE_FORMAT.format(versionInfo.getTimestamp());
            JLabel label = new JLabel("🕐 " + formattedTime);
            label.setFont(label.getFont().deriveFont(Font.PLAIN, (float) METADATA_FONT_SIZE));
            label.setForeground(TIMESTAMP_COLOR);
            return label;
        }
    }
}
