package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.domain.model.GitBranchInfo;
import com.pydlove.workcove.development.domain.model.GitCommitInfo;
import com.pydlove.workcove.development.domain.model.GitOperationCommand;
import com.pydlove.workcove.development.domain.model.GitOperationResult;
import com.pydlove.workcove.development.domain.model.GitOperationType;
import com.pydlove.workcove.development.domain.model.GitRepositoryStatus;
import com.pydlove.workcove.development.domain.port.GitRepositoryGateway;
import com.pydlove.workcove.shared.domain.DomainException;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class GitRepositoryService {

    private static final int MAX_PATH_LENGTH = 1024;
    private static final int MAX_BRANCH_LENGTH = 255;
    private static final int MAX_COMMIT_MESSAGE_LENGTH = 4000;

    private final GitRepositoryGateway gateway;

    public GitRepositoryService(GitRepositoryGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "Git 仓库网关");
    }

    public GitRepositoryStatus inspect(String rawPath) {
        return gateway.inspect(normalizePath(rawPath));
    }

    public List<GitBranchInfo> branches(String rawPath) {
        return gateway.branches(normalizePath(rawPath));
    }

    public List<GitCommitInfo> log(String rawPath, int limit) {
        if (limit < 1 || limit > 200) {
            throw new DomainException("Git 日志数量必须在 1 到 200 之间");
        }
        return gateway.log(normalizePath(rawPath), limit);
    }

    public GitOperationResult execute(
            String rawPath,
            GitOperationCommand rawCommand) {
        if (rawCommand == null || rawCommand.type() == null) {
            throw new DomainException("请选择 Git 操作");
        }
        String path = normalizePath(rawPath);
        String branchName = value(rawCommand.branchName());
        String sourceBranch = value(rawCommand.sourceBranch());
        String commitMessage = value(rawCommand.commitMessage());

        switch (rawCommand.type()) {
            case CREATE_BRANCH, SWITCH_BRANCH, DELETE_BRANCH ->
                    requireBranch(branchName);
            case MERGE -> requireBranch(sourceBranch);
            case COMMIT -> requireCommitMessage(commitMessage);
            case PUSH -> {
                if (rawCommand.setUpstream()) {
                    requireBranch(branchName);
                }
            }
            default -> {
            }
        }
        return gateway.execute(
                path,
                new GitOperationCommand(
                        rawCommand.type(),
                        branchName,
                        sourceBranch,
                        commitMessage,
                        rawCommand.force(),
                        rawCommand.setUpstream()));
    }

    public static boolean isNetworkOperation(GitOperationType type) {
        return type == GitOperationType.FETCH
                || type == GitOperationType.PULL
                || type == GitOperationType.PUSH;
    }

    private static void requireBranch(String value) {
        if (value.isBlank()) {
            throw new DomainException("请输入分支名称");
        }
        if (value.length() > MAX_BRANCH_LENGTH
                || value.startsWith("-")
                || value.contains("\n")
                || value.contains("\r")) {
            throw new DomainException("分支名称无效");
        }
    }

    private static void requireCommitMessage(String value) {
        if (value.isBlank()) {
            throw new DomainException("请输入提交说明");
        }
        if (value.length() > MAX_COMMIT_MESSAGE_LENGTH) {
            throw new DomainException("提交说明过长");
        }
    }

    private static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new DomainException("请选择 Git 仓库目录");
        }
        String value = rawPath.trim();
        if (value.length() > MAX_PATH_LENGTH) {
            throw new DomainException("Git 仓库路径过长");
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new DomainException("Git 仓库路径无效");
        }

        Path path;
        try {
            path = Path.of(value);
        } catch (InvalidPathException exception) {
            throw new DomainException("Git 仓库路径无效", exception);
        }
        if (!path.isAbsolute()) {
            throw new DomainException("Git 仓库必须使用绝对路径");
        }
        return path.normalize().toString();
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
