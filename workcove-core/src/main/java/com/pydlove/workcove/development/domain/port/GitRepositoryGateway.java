package com.pydlove.workcove.development.domain.port;

import com.pydlove.workcove.development.domain.model.GitBranchInfo;
import com.pydlove.workcove.development.domain.model.GitCommitInfo;
import com.pydlove.workcove.development.domain.model.GitFileContent;
import com.pydlove.workcove.development.domain.model.GitOperationCommand;
import com.pydlove.workcove.development.domain.model.GitOperationResult;
import com.pydlove.workcove.development.domain.model.GitRepositoryStatistics;
import com.pydlove.workcove.development.domain.model.GitRepositoryStatus;
import com.pydlove.workcove.development.domain.model.GitReviewContext;

import java.util.List;

public interface GitRepositoryGateway {

    GitRepositoryStatus inspect(String path);

    List<GitBranchInfo> branches(String path);

    List<GitCommitInfo> log(String path, int limit);

    List<GitCommitInfo> authorCommits(
            String path,
            String authorName,
            String authorEmail);

    GitRepositoryStatistics statistics(String path);

    GitReviewContext reviewContext(
            String path,
            String branch,
            String baseBranch,
            String fromCommit);

    default GitReviewContext reviewContext(
            String path,
            String branch,
            String baseBranch,
            String fromCommit,
            String since,
            String until) {
        return reviewContext(path, branch, baseBranch, fromCommit);
    }

    GitFileContent fileContent(
            String path,
            String revision,
            String filePath,
            int line);

    GitOperationResult execute(
            String path,
            GitOperationCommand command);
}
