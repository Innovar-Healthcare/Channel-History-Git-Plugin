package com.innovarhealthcare.channelHistory.client.panel;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Font;

import com.mirth.connect.client.ui.UIConstants;
import net.miginfocom.swing.MigLayout;

/**
 * @author Thai Tran
 * @create 2025-04-30 10:00 AM
 */
public class GitStatusTabPanel extends JPanel {

    private JLabel gitStatusTbdLabel;
    private JLabel gitStatusDescLabel;

    public GitStatusTabPanel() {
        initComponents();
        initLayout();
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        gitStatusTbdLabel = new JLabel("TBD");
        gitStatusTbdLabel.setFont(new Font("Tahoma", Font.BOLD, 18));
        gitStatusTbdLabel.setForeground(Color.GRAY);

        gitStatusDescLabel = new JLabel("<html><center>This tab will display the local Git repository status<br>and allow users to perform Git actions directly from Mirth Connect.<br><br><b>Planned actions:</b> Status &nbsp;|&nbsp; Commit &nbsp;|&nbsp; Push &nbsp;|&nbsp; Revert &nbsp;|&nbsp; Diff</center></html>");
        gitStatusDescLabel.setFont(new Font("Tahoma", Font.PLAIN, 11));
        gitStatusDescLabel.setForeground(Color.GRAY);
    }

    private void initLayout() {
        setLayout(new MigLayout("fill, insets 16", "[grow, center]"));
        add(gitStatusTbdLabel, "wrap, align center");
        add(gitStatusDescLabel, "align center");
    }
}
