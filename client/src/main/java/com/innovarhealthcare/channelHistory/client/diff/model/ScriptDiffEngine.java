package com.innovarhealthcare.channelHistory.client.diff.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

public class ScriptDiffEngine {

    public static DiffResult computeDiff(String leftText, String rightText) {
        List<String> leftLines = splitIntoLines(leftText);
        List<String> rightLines = splitIntoLines(rightText);

        Patch<String> patch = DiffUtils.diff(leftLines, rightLines);

        return buildDiffResult(leftLines, rightLines, patch);
    }

    private static List<String> splitIntoLines(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(text.split("\n", -1));
    }

    private static DiffResult buildDiffResult(List<String> leftLines, List<String> rightLines, Patch<String> patch) {

        Set<Integer> leftChangedLines = new HashSet<>();
        Set<Integer> rightChangedLines = new HashSet<>();

        for (AbstractDelta<String> delta : patch.getDeltas()) {
            switch (delta.getType()) {
                case DELETE:
                    for (int i = delta.getSource().getPosition(); i < delta.getSource().getPosition() + delta.getSource().size(); i++) {
                        leftChangedLines.add(i);
                    }
                    break;
                case INSERT:
                    for (int i = delta.getTarget().getPosition(); i < delta.getTarget().getPosition() + delta.getTarget().size(); i++) {
                        rightChangedLines.add(i);
                    }
                    break;
                case CHANGE:
                    for (int i = delta.getSource().getPosition(); i < delta.getSource().getPosition() + delta.getSource().size(); i++) {
                        leftChangedLines.add(i);
                    }
                    for (int i = delta.getTarget().getPosition(); i < delta.getTarget().getPosition() + delta.getTarget().size(); i++) {
                        rightChangedLines.add(i);
                    }
                    break;
            }
        }

        List<DiffLine> left = new ArrayList<>();
        for (int i = 0; i < leftLines.size(); i++) {
            ChangeType type = leftChangedLines.contains(i) ? ChangeType.DELETED : ChangeType.UNCHANGED;
            left.add(new DiffLine(i + 1, leftLines.get(i), type));
        }

        List<DiffLine> right = new ArrayList<>();
        for (int i = 0; i < rightLines.size(); i++) {
            ChangeType type = rightChangedLines.contains(i) ? ChangeType.ADDED : ChangeType.UNCHANGED;
            right.add(new DiffLine(i + 1, rightLines.get(i), type));
        }

        return new DiffResult(left, right);
    }
}
