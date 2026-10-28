package com.pydlove.workcove.development.domain.model;

public record GitChangedFile(
        String path,
        String indexStatus,
        String workTreeStatus,
        boolean staged,
        boolean unstaged,
        boolean untracked,
        boolean conflicted) {
}
