package com.pydlove.workcove.development.domain.port;

import com.pydlove.workcove.development.domain.model.GitBranchInfo;
import com.pydlove.workcove.development.domain.model.GitCommitInfo;
import com.pydlove.workcove.development.domain.model.GitOperationCommand;
import com.pydlove.workcove.development.domain.model.GitOperationResult;
import com.pydlove.workcove.development.domain.model.GitRepositoryStatus;

import java.util.List;

public interface GitRepositoryGateway {

    GitRepositoryStatus inspect(String path);

    List<GitBranchInfo> branches(String path);

    List<GitCommitInfo> log(String path, int limit);

    GitOperationResult execute(
            String path,
            GitOperationCommand command);
}
