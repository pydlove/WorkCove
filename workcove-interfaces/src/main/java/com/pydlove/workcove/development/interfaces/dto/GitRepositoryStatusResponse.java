package com.pydlove.workcove.development.interfaces.dto;

import com.pydlove.workcove.development.domain.model.GitRepositoryStatus;

import java.util.List;

public record GitRepositoryStatusResponse(
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
        boolean clean,
        String latestCommitHash,
        String latestCommitShortHash,
        String latestCommitSubject,
        String latestCommitAt,
        boolean mergeInProgress,
        List<ChangedFileResponse> changedFiles,
        List<String> conflictFiles) {

    public static GitRepositoryStatusResponse from(GitRepositoryStatus status) {
        return new GitRepositoryStatusResponse(
                status.rootPath(),
                status.name(),
                status.branch(),
                status.remoteName(),
                status.remoteUrl(),
                status.ahead(),
                status.behind(),
                status.staged(),
                status.unstaged(),
                status.untracked(),
                status.conflicted(),
                status.clean(),
                status.latestCommitHash(),
                status.latestCommitShortHash(),
                status.latestCommitSubject(),
                status.latestCommitAt(),
                status.mergeInProgress(),
                status.changedFiles().stream()
                        .map(ChangedFileResponse::from)
                        .toList(),
                status.conflictFiles());
    }

    public record ChangedFileResponse(
            String path,
            String indexStatus,
            String workTreeStatus,
            boolean staged,
            boolean unstaged,
            boolean untracked,
            boolean conflicted) {

        public static ChangedFileResponse from(
                com.pydlove.workcove.development.domain.model.GitChangedFile file) {
            return new ChangedFileResponse(
                    file.path(),
                    file.indexStatus(),
                    file.workTreeStatus(),
                    file.staged(),
                    file.unstaged(),
                    file.untracked(),
                    file.conflicted());
        }
    }
}
