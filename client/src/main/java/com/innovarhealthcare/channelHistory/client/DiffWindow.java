/*
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.innovarhealthcare.channelHistory.client;

import com.innovarhealthcare.channelHistory.shared.ObjectDiff;
import com.kayyagari.objmeld.OgnlComparison;
import com.kayyagari.objmeld.StringContent;
import com.mirth.connect.client.ui.ChannelSetup;
import com.mirth.connect.client.ui.MirthDialog;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.model.Channel;


import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Collections;

/**
 * The main window for showing diff.
 * 
 * @author Kiran Ayyagari (kayyagari@apache.org)
 */
public class DiffWindow extends MirthDialog {
    private Object left;
    private Object right;
    private String leftLabel;
    private String rightLabel;
    private JTabbedPane tabbedPane;
    private JPanel objDiffPanel = null; // object diff panel

    
    private JPanel labelPanel;
    private DiffWindow(String title, String leftLabel, String rightLabel, Object left, Object right,Window parent) {
        super(parent);
        this.left = left;
        this.right = right;
        this.leftLabel = leftLabel;
        this.rightLabel = rightLabel;
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);

        this.labelPanel = new JPanel(new BorderLayout());
        this.labelPanel.add(new JLabel("\t" + leftLabel), BorderLayout.EAST);
        this.labelPanel.add(new JLabel(rightLabel + "\t"), BorderLayout.WEST);

        setTitle(title);
        add(tabbedPane);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(PlatformUI.MIRTH_FRAME);
    }

    public static DiffWindow create(String title, String leftLabel, String rightLabel, Object leftObj, Object rightObj, String leftStrContent, String rightStrContent, Window parent) {
        DiffWindow dd = new DiffWindow(title, leftLabel, rightLabel, leftObj, rightObj, parent);
        dd.prepareTextView(leftStrContent, rightStrContent);
        dd.prepareObjectView();
        //dd.prepareChannelView();

        return dd;
    }
    
    private void prepareObjectView() {
        try {
            ObjectDiff od = new ObjectDiff(left, right);
            od.create();
            objDiffPanel = od.getVisualPanel();
        }
        catch(Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            JTextArea errorArea = new JTextArea();
            errorArea.setText(sw.toString());
            objDiffPanel = new JPanel(new BorderLayout());
            objDiffPanel.add(errorArea);
        }
        
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(labelPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(objDiffPanel), BorderLayout.CENTER);
        tabbedPane.add("Object View", panel);
    }
    private void prepareTextView(String leftStrContent, String rightStrContent) {
        JPanel panel = OgnlComparison.prepare(Collections.singletonList(new StringContent("", leftStrContent)), Collections.singletonList(new StringContent("", rightStrContent)), true);
        JPanel panel2 = new JPanel(new BorderLayout());
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.add(new JLabel("\t" + this.leftLabel), BorderLayout.EAST);
        infoPanel.add(new JLabel(this.rightLabel + "\t"), BorderLayout.WEST);
        panel2.add(infoPanel, BorderLayout.NORTH);

        panel2.add(panel);
        tabbedPane.add("XML View", panel2);
    }
    
    private void prepareChannelView() {
        if(!(left instanceof Channel && right instanceof Channel)) {
           return; 
        }
        
        JSplitPane channelPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        channelPane.setDividerLocation(0.5);
        ChannelSetup channelLeft = new ChannelSetup();
        channelLeft.addChannel((Channel)left, "");
        channelPane.setLeftComponent(channelLeft);

        ChannelSetup channelRight = new ChannelSetup();
        channelRight.addChannel((Channel)right, "");
        channelPane.setRightComponent(channelRight);

        tabbedPane.add("Channel View", channelPane);
    }
    

    

}
