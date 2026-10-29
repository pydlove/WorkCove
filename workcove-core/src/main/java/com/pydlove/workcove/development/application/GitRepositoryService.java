package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.domain.model.GitBranchInfo;
import com.pydlove.workcove.development.domain.model.GitCommitInfo;
import com.pydlove.workcove.development.domain.model.GitFileContent;
import com.pydlove.workcove.development.domain.model.GitOperationCommand;
import com.pydlove.workcove.development.domain.model.GitOperationResult;
import com.pydlove.workcove.development.domain.model.GitOperationType;
import com.pydlove.workcove.development.domain.model.GitRepositoryStatistics;
import com.pydlove.workcove.development.domain.model.GitRepositoryStatus;
import com.pydlove.workcove.development.domain.model.GitReviewContext;
import com.pydlove.workcove.development.domain.port.GitRepositoryGateway;
import com.pydlove.workcove.shared.domain.DomainException;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class GitRepositoryService {

    private static final int MAX_PATH_LENGTH = 1024;
    private static final int MAX_BRANCH_LENGTH = 255;
    private static final int MAX_COMMIT_MESSAGE_LENGTH = 4000;
    private static final int MAX_AUTHOR_NAME_LENGTH = 200;
    private static final int MAX_AUTHOR_EMAIL_LENGTH = 320;
    private static final int MAX_FILE_PATH_LENGTH = 2048;
    private static final int MAX_FILE_LINE = 10_000_000;

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

    public List<GitCommitInfo> authorCommits(
            String rawPath,
            String rawAuthorName,
            String rawAuthorEmail) {
        String authorName = value(rawAuthorName);
        String authorEmail = value(rawAuthorEmail);
        if (authorName.isEmpty() && authorEmail.isEmpty()) {
            throw new DomainException("缺少 Git 提交作者信息");
        }
        if (authorName.length() > MAX_AUTHOR_NAME_LENGTH) {
            throw new DomainException("Git 提交作者名称过长");
        }
        if (authorEmail.length() > MAX_AUTHOR_EMAIL_LENGTH) {
            throw new DomainException("Git 提交作者邮箱过长");
        }
        if (authorName.indexOf('\n') >= 0
                || authorName.indexOf('\r') >= 0
                || authorEmail.indexOf('\n') >= 0
                || authorEmail.indexOf('\r') >= 0) {
            throw new DomainException("Git 提交作者信息无效");
        }
        return gateway.authorCommits(
                normalizePath(rawPath),
                authorName,
                authorEmail);
    }

    public GitRepositoryStatistics statistics(String rawPath) {
        return gateway.statistics(normalizePath(rawPath));
    }

    public GitReviewContext reviewContext(
            String rawPath,
            String rawBranch,
            String rawBaseBranch,
            String rawFromCommit,
            String rawSince,
            String rawUntil) {
        String branch = branchRef(rawBranch, "请选择评审分支");
        String baseBranch = branchRef(rawBaseBranch, "请选择对比分支");
        String fromCommit = value(rawFromCommit);
        if (!fromCommit.isEmpty()
                && !fromCommit.matches("[0-9a-fA-F]{4,64}")) {
            throw new DomainException("评审起始提交无效");
        }
        String since = reviewDate(rawSince);
        String until = reviewDate(rawUntil);
        if (!since.isEmpty()
                && !until.isEmpty()
                && until.compareTo(since) < 0) {
            throw new DomainException("评审结束日期不能早于开始日期");
        }
        return gateway.reviewContext(
                normalizePath(rawPath),
                branch,
                baseBranch,
                fromCommit,
                since,
                until);
    }

    public GitFileContent fileContent(
            String rawPath,
            String rawRevision,
            String rawFilePath,
            int line) {
        String revision = value(rawRevision);
        if (!revision.matches("[0-9a-fA-F]{4,64}")) {
            throw new DomainException("评审提交版本无效");
        }
        if (line < 0 || line > MAX_FILE_LINE) {
            throw new DomainException("文件行号无效");
        }
        return gateway.fileContent(
                normalizePath(rawPath),
                revision.toLowerCase(Locale.ROOT),
                normalizeRepositoryFile(rawFilePath),
                line);
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

    private static String branchRef(String value, String message) {
        String branch = value(value);
        if (branch.isEmpty()) {
            throw new DomainException(message);
        }
        if (branch.length() > MAX_BRANCH_LENGTH
                || branch.startsWith("-")
                || branch.indexOf('\n') >= 0
                || branch.indexOf('\r') >= 0) {
            throw new DomainException("Git 分支名称无效");
        }
        return branch;
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

    private static String normalizeRepositoryFile(String rawPath) {
        String value = value(rawPath);
        if (value.isEmpty()) {
            throw new DomainException("请选择需要查看的文件");
        }
        if (value.length() > MAX_FILE_PATH_LENGTH
                || value.startsWith("/")
                || value.startsWith("\\")
                || value.indexOf('\\') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0
                || value.indexOf('\0') >= 0) {
            throw new DomainException("文件路径无效");
        }

        Path path;
        try {
            path = Path.of(value).normalize();
        } catch (InvalidPathException exception) {
            throw new DomainException("文件路径无效", exception);
        }
        if (path.isAbsolute()
                || path.getNameCount() == 0
                || path.startsWith("..")
                || ".".equals(path.toString())) {
            throw new DomainException("文件必须位于 Git 仓库目录内");
        }
        return path.toString().replace('\\', '/');
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    private static String reviewDate(String rawValue) {
        String value = value(rawValue);
        if (value.isEmpty()) return "";
        try {
            return LocalDate.parse(value).toString();
        } catch (DateTimeParseException exception) {
            throw new DomainException("评审日期格式应为 YYYY-MM-DD");
        }
    }
}
