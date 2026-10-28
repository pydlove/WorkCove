package com.pydlove.workcove.development.domain.model;

import java.util.List;

public record GitRepositoryStatus(
        String rootPath,
        String name,
        String branch,
        String remoteName,
        String remoteUrl,
        int ahead,
        int behind,
        int staged,
        int unstaged,
        int untracked,
        int conflicted,
        String latestCommitHash,
        String latestCommitShortHash,
        String latestCommitSubject,
        String latestCommitAt,
        boolean mergeInProgress,
        List<GitChangedFile> changedFiles,
        List<String> conflictFiles) {

    public boolean clean() {
        return !mergeInProgress
                && staged == 0
                && unstaged == 0
                && untracked == 0
                && conflicted == 0;
    }
}
