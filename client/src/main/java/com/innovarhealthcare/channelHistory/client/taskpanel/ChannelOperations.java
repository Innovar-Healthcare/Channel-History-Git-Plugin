package com.innovarhealthcare.channelHistory.client.taskpanel;

import com.innovarhealthcare.channelHistory.client.dialog.ImportChannelDialog;
import com.mirth.connect.client.ui.Frame;

/**
 * Business operations for Channel context.
 */
public class ChannelOperations {

    private final Frame parent;

    public ChannelOperations(Frame parent) {
        this.parent = parent;
    }

    public void showDiff() {
        // TODO: Implement
    }

    public void commitAndPush() {
        // TODO: Implement
    }

    public void pull() {
        // TODO: Implement
    }

    public void revert() {
        // TODO: Implement
    }

    public void importChannel() {
        new ImportChannelDialog(parent);
    }
}
