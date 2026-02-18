package com.innovarhealthcare.channelHistory.client.diff.model;

import java.util.List;

public class DiffResult {
    private final List<DiffLine> leftLines;
    private final List<DiffLine> rightLines;

    public DiffResult(List<DiffLine> leftLines, List<DiffLine> rightLines) {
        this.leftLines = leftLines;
        this.rightLines = rightLines;
    }

    public List<DiffLine> getLeftLines() {
        return leftLines;
    }

    public List<DiffLine> getRightLines() {
        return rightLines;
    }
}
