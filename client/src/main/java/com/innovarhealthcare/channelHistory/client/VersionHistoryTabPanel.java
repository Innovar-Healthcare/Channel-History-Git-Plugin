package com.innovarhealthcare.channelHistory.client;

import com.innovarhealthcare.channelHistory.shared.interfaces.channelHistoryServletInterface;
import com.innovarhealthcare.channelHistory.shared.RevisionInfo;

import com.mirth.connect.client.ui.AbstractChannelTabPanel;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.InvalidChannel;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import org.apache.log4j.Logger;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @author Kiran Ayyagari (kayyagari@apache.org)
 */
public class VersionHistoryTabPanel extends AbstractChannelTabPanel {
    private static Logger log = Logger.getLogger(VersionHistoryTabPanel.class);

    private RevisionInfoTable tblRevisions;

    private channelHistoryServletInterface gitServlet;
    private static DateFormat df = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");

    private String cid;

    private JPopupMenu popupMenu;
    
    private JMenuItem mnuShowDiff;

    private Frame parent;
    
    private ObjectXMLSerializer serializer;

    public VersionHistoryTabPanel(Frame parent) {
        this.parent = parent;
        this.serializer = ObjectXMLSerializer.getInstance();
        setLayout(new BorderLayout());

        tblRevisions = new RevisionInfoTable();
        tblRevisions.setRowSelectionAllowed(true);
        tblRevisions.setColumnSelectionAllowed(false);
        tblRevisions.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        popupMenu = new JPopupMenu();
        mnuShowDiff = new JMenuItem("Show Diff");
        mnuShowDiff.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showDiffWindow();
            }
        });
        popupMenu.add(mnuShowDiff);

        MouseAdapter popupListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handlePopupEvent(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handlePopupEvent(e);
            }

            public void handlePopupEvent(MouseEvent e) {
                if(e.isPopupTrigger()) {
                    //System.out.println("popup triggered");
                    mnuShowDiff.setEnabled(tblRevisions.getSelectedRowCount() == 2);
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        };
        tblRevisions.addMouseListener(popupListener);

        JScrollPane scrollPane = new JScrollPane(tblRevisions);

        scrollPane.addMouseListener(popupListener);
        add(scrollPane);

        parent.addTask("loadHistory", "Refresh history", "Refresh version history.", "", new ImageIcon(Frame.class.getResource("images/arrow_refresh.png")), parent.channelEditTasks, parent.channelEditPopupMenu, this);
    }

    @Override
    public void load(Channel channel) {
        cid = channel.getId();
        this.loadHistory(false);
    }

    @Override
    public void save(Channel channel) {
        log.info("saving channel " + channel.getId());
    }

    public void loadHistory() {
        this.loadHistory(true);
    }

    public void loadHistory(boolean shouldNotifyOnComplete) {
        SwingUtilities.invokeLater(new LoadGitHistoryRunnable(shouldNotifyOnComplete));
    }
    
    private void showDiffWindow() {
        popupMenu.setVisible(false);
        int[] rows = tblRevisions.getSelectedRows();
        RevisionInfoTableModel model = (RevisionInfoTableModel)tblRevisions.getModel();
        RevisionInfo ri1 = model.getRevisionAt(rows[0]);
        RevisionInfo ri2 = model.getRevisionAt(rows[1]);

        try {
            String left = gitServlet.getContent(cid, ri1.getHash(), "Channel");
            Channel leftCh = parse(left, ri1.getShortHash());
            String right = gitServlet.getContent(cid, ri2.getHash(),"Channel");
            Channel rightCh = parse(right, ri2.getShortHash());

            String labelPrefix = leftCh.getName();
            String leftLabel = labelPrefix + " Time: " + df.format(new Date(ri1.getTime())) + " Committed by " +ri1.getCommitterName();
            String rightLabel = labelPrefix + " Time: " + df.format(new Date(ri2.getTime())) + " Committed by " +ri1.getCommitterName();

            DiffWindow dw = DiffWindow.create("Channel Diff", leftLabel, rightLabel, leftCh, rightCh, left, right, parent);
            dw.setSize(parent.getWidth() - 10, parent.getHeight()-10);
            dw.setVisible(true);
        }
        catch(Exception e) {
            PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e);
        }
    }
    
    private Channel parse(String xml, String rev) {
        Channel ch = serializer.deserialize(xml, Channel.class);
        if(ch instanceof InvalidChannel) {
            throw new IllegalStateException("could not parse channel at revision " + rev);
        }
        
        return ch;
    }

    private class LoadGitHistoryRunnable implements Runnable {
        private boolean shouldNotifyOnComplete;

        LoadGitHistoryRunnable(boolean shouldNotifyOnComplete) {
            this.shouldNotifyOnComplete = shouldNotifyOnComplete;
        }

        @Override
        public void run() {
            try {
                // initialize once
                // doing here because do not want to delay the startup of MC client which takes several seconds to start.
                if(gitServlet == null) {
                    gitServlet = parent.mirthClient.getServlet(channelHistoryServletInterface.class);
                }

                // then fetch revisions
                List<String> revisions = gitServlet.getHistory(cid, "Channel");
                RevisionInfoTableModel model = new RevisionInfoTableModel(revisions);
                tblRevisions.setModel(model);
                if (shouldNotifyOnComplete) {
                    PlatformUI.MIRTH_FRAME.alertInformation(parent, "History refreshed!");
                }
            } catch (Exception e) {
                PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e);
            }
        }
    }

}
