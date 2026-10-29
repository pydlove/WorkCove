package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.development.domain.model.GitBranchInfo;
import com.pydlove.workcove.development.domain.model.GitAuthorStatistics;
import com.pydlove.workcove.development.domain.model.GitChangedFile;
import com.pydlove.workcove.development.domain.model.GitCommitInfo;
import com.pydlove.workcove.development.domain.model.GitFileContent;
import com.pydlove.workcove.development.domain.model.GitHourStatistics;
import com.pydlove.workcove.development.domain.model.GitMonthStatistics;
import com.pydlove.workcove.development.domain.model.GitOperationCommand;
import com.pydlove.workcove.development.domain.model.GitOperationResult;
import com.pydlove.workcove.development.domain.model.GitOperationType;
import com.pydlove.workcove.development.domain.model.GitRepositoryStatistics;
import com.pydlove.workcove.development.domain.model.GitRepositoryStatus;
import com.pydlove.workcove.development.domain.model.GitReviewContext;
import com.pydlove.workcove.development.domain.port.GitRepositoryGateway;
import com.pydlove.workcove.shared.domain.DomainException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProcessGitRepositoryGateway implements GitRepositoryGateway {

    private static final int MAX_OUTPUT_BYTES = 1024 * 1024;
    private static final int MAX_STATISTICS_OUTPUT_BYTES = 64 * 1024 * 1024;
    private static final int COMMAND_TIMEOUT_SECONDS = 8;
    private static final int STATISTICS_TIMEOUT_SECONDS = 120;
    private static final int REVIEW_TIMEOUT_SECONDS = 30;
    private static final int MAX_REVIEW_OUTPUT_BYTES = 16 * 1024 * 1024;
    private static final int MAX_FILE_CONTENT_OUTPUT_BYTES =
            4 * 1024 * 1024;
    private static final int FILE_CONTEXT_LINES = 80;
    private static final long MAX_TRACKED_FILE_BYTES = 20L * 1024 * 1024;
    private static final Set<String> CONFLICT_CODES = Set.of(
            "DD", "AU", "UD", "UA", "DU", "AA", "UU");
    private static final Pattern AHEAD_PATTERN =
            Pattern.compile("ahead (\\d+)");
    private static final Pattern BEHIND_PATTERN =
            Pattern.compile("behind (\\d+)");

    @Override
    public GitRepositoryStatus inspect(String path) {
        String rootPath = repositoryRoot(path);
        CommandResult statusResult = run(
                rootPath,
                "-c",
                "core.quotepath=false",
                "status",
                "--porcelain=v1",
                "-z",
                "--branch");
        if (statusResult.exitCode() != 0) {
            throw new DomainException("无法读取 Git 仓库状态");
        }

        StatusSummary summary = parseStatus(statusResult.output());
        RemoteInfo remote = remote(rootPath);
        CommitInfo commit = latestCommit(rootPath);
        Path fileName = Path.of(rootPath).getFileName();
        return new GitRepositoryStatus(
                rootPath,
                fileName == null ? rootPath : fileName.toString(),
                summary.branch(),
                remote.name(),
                remote.url(),
                summary.ahead(),
                summary.behind(),
                summary.staged(),
                summary.unstaged(),
                summary.untracked(),
                summary.conflicted(),
                commit.hash(),
                commit.shortHash(),
                commit.subject(),
                commit.committedAt(),
                mergeInProgress(rootPath),
                summary.changedFiles(),
                summary.conflictFiles());
    }

    @Override
    public List<GitBranchInfo> branches(String path) {
        String rootPath = repositoryRoot(path);
        CommandResult result = run(
                rootPath,
                30,
                "for-each-ref",
                "--sort=refname",
                "--format=%(refname)%00%(refname:short)%00%(HEAD)%00%(upstream:short)%00%(objectname:short)%00%(committerdate:iso-strict)",
                "refs/heads",
                "refs/remotes");
        if (result.exitCode() != 0) {
            throw new DomainException("无法读取 Git 分支");
        }

        List<GitBranchInfo> branches = new ArrayList<>();
        for (String line : result.output().split("\\R")) {
            if (line.isBlank()) continue;
            String[] values = line.split("\u0000", -1);
            if (values.length < 6) continue;
            String refName = values[0];
            if (refName.endsWith("/HEAD")) continue;
            branches.add(new GitBranchInfo(
                    values[1],
                    refName,
                    "*".equals(values[2]),
                    refName.startsWith("refs/remotes/"),
                    values[3],
                    values[4],
                    values[5]));
        }
        return branches;
    }

    @Override
    public List<GitCommitInfo> log(String path, int limit) {
        String rootPath = repositoryRoot(path);
        CommandResult head = run(
                rootPath,
                "rev-parse",
                "--verify",
                "HEAD");
        if (head.exitCode() != 0) return List.of();

        CommandResult result = run(
                rootPath,
                30,
                "log",
                "--max-count=" + limit,
                "--no-show-signature",
                "--format=%H%x00%h%x00%P%x00%an%x00%ae%x00%aI%x00%s%x1e");
        if (result.exitCode() != 0) {
            throw new DomainException("无法读取 Git 提交历史");
        }
        return parseCommitLog(result.output());
    }

    @Override
    public List<GitCommitInfo> authorCommits(
            String path,
            String authorName,
            String authorEmail) {
        String rootPath = repositoryRoot(path);
        CommandResult head = run(
                rootPath,
                "rev-parse",
                "--verify",
                "HEAD");
        if (head.exitCode() != 0) return List.of();

        LocalDate since = LocalDate.now().minusYears(1);
        CommandResult result = runWithin(
                rootPath,
                STATISTICS_TIMEOUT_SECONDS,
                MAX_STATISTICS_OUTPUT_BYTES,
                "-c",
                "core.quotepath=false",
                "log",
                "--since=" + since,
                "--no-merges",
                "--no-show-signature",
                "--format=%H%x00%h%x00%P%x00%an%x00%ae%x00%aI%x00%s%x1e");
        if (result.exitCode() != 0 || result.truncated()) {
            throw new DomainException(
                    result.truncated()
                            ? "Git 提交历史过大，无法读取作者记录"
                            : "无法读取该作者的 Git 提交记录");
        }
        String email = authorEmail.trim();
        String name = authorName.trim();
        return parseCommitLog(result.output()).stream()
                .filter(commit -> email.isBlank()
                        ? name.equalsIgnoreCase(commit.authorName().trim())
                        : email.equalsIgnoreCase(commit.authorEmail().trim()))
                .toList();
    }

    private static List<GitCommitInfo> parseCommitLog(String output) {
        List<GitCommitInfo> commits = new ArrayList<>();
        for (String record : output.split("\u001e")) {
            String value = record.strip();
            if (value.isEmpty()) continue;
            String[] fields = value.split("\u0000", -1);
            if (fields.length < 7) continue;
            commits.add(new GitCommitInfo(
                    fields[0],
                    fields[1],
                    fields[2],
                    fields[3],
                    fields[4],
                    fields[5],
                    fields[6]));
        }
        return commits;
    }

    private static String mergeBase(
            String rootPath,
            String baseCommit,
            String headCommit) {
        CommandResult result = run(
                rootPath,
                "merge-base",
                baseCommit,
                headCommit);
        String value = result.output().trim();
        if (result.exitCode() != 0 || value.isEmpty()) {
            throw new DomainException(
                    "两个分支没有共同祖先，无法发起代码评审");
        }
        return value;
    }

    private static String resolveCommit(
            String rootPath,
            String ref,
            String errorMessage) {
        CommandResult result = run(
                rootPath,
                "rev-parse",
                "--verify",
                "--end-of-options",
                ref + "^{commit}");
        String value = result.output().trim();
        if (result.exitCode() != 0 || value.isEmpty()) {
            throw new DomainException(errorMessage);
        }
        return value;
    }

    private static List<String> splitFileLines(String output) {
        String normalized = output
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        if (normalized.endsWith("\n")) {
            normalized = normalized.substring(
                    0,
                    normalized.length() - 1);
        }
        if (normalized.isEmpty()) return List.of();
        return Arrays.asList(normalized.split("\n", -1));
    }

    private static DiffSummary parseDiffSummary(String output) {
        int fileCount = 0;
        long additions = 0;
        long deletions = 0;
        for (String line : output.split("\\R")) {
            if (line.isBlank()) continue;
            String[] values = line.split("\t", -1);
            if (values.length < 3) continue;
            fileCount += 1;
            additions += parseStat(values[0]);
            deletions += parseStat(values[1]);
        }
        return new DiffSummary(fileCount, additions, deletions);
    }

    @Override
    public GitRepositoryStatistics statistics(String path) {
        String rootPath = repositoryRoot(path);
        LocalDate until = LocalDate.now();
        LocalDate since = until.minusYears(1);
        List<YearMonth> monthValues = new ArrayList<>();
        Map<String, Integer> monthIndexes = new HashMap<>();
        for (int offset = 11; offset >= 0; offset -= 1) {
            YearMonth value = YearMonth.from(until).minusMonths(offset);
            monthIndexes.put(value.toString(), monthValues.size());
            monthValues.add(value);
        }
        long[][] monthTotals = new long[monthValues.size()][3];
        long[] hourTotals = new long[24];
        Map<String, AuthorAccumulator> authors = new HashMap<>();
        Set<String> changedFiles = new HashSet<>();
        long totalCommits = 0;
        long additions = 0;
        long deletions = 0;

        CommandResult head = run(rootPath, "rev-parse", "--verify", "HEAD");
        if (head.exitCode() == 0) {
            CommandResult result = runWithin(
                    rootPath,
                    STATISTICS_TIMEOUT_SECONDS,
                    MAX_STATISTICS_OUTPUT_BYTES,
                    "-c",
                    "core.quotepath=false",
                    "log",
                    "--since=" + since,
                    "--no-merges",
                    "--numstat",
                    "--no-renames",
                    "--format=%x1e%H%x00%an%x00%ae%x00%aI");
            if (result.exitCode() != 0 || result.truncated()) {
                throw new DomainException(
                        result.truncated()
                                ? "Git 统计数据过大，请缩小仓库范围"
                                : "无法统计 Git 提交历史");
            }

            for (String rawRecord : result.output().split("\u001e", -1)) {
                String record = rawRecord.strip();
                if (record.isEmpty()) continue;
                String[] sections = record.split("\\R", 2);
                String[] fields = sections[0].split("\u0000", -1);
                if (fields.length < 4) continue;

                OffsetDateTime committedAt;
                try {
                    committedAt = OffsetDateTime.parse(fields[3].trim());
                } catch (Exception ignored) {
                    continue;
                }
                String name = fields[1].isBlank()
                        ? "未知作者"
                        : fields[1].trim();
                String email = fields[2].trim().toLowerCase(Locale.ROOT);
                String authorKey = email.isBlank()
                        ? name.toLowerCase(Locale.ROOT)
                        : email;
                AuthorAccumulator author = authors.computeIfAbsent(
                        authorKey,
                        ignored -> new AuthorAccumulator(name, email));
                author.commits += 1;
                totalCommits += 1;
                hourTotals[committedAt.getHour()] += 1;

                Integer monthIndex = monthIndexes.get(
                        YearMonth.from(committedAt).toString());
                if (monthIndex != null) {
                    monthTotals[monthIndex][0] += 1;
                }

                if (sections.length < 2) continue;
                for (String line : sections[1].split("\\R")) {
                    if (line.isBlank()) continue;
                    String[] values = line.split("\t", -1);
                    if (values.length < 3) continue;
                    long fileAdditions = parseStat(values[0]);
                    long fileDeletions = parseStat(values[1]);
                    additions += fileAdditions;
                    deletions += fileDeletions;
                    author.additions += fileAdditions;
                    author.deletions += fileDeletions;
                    changedFiles.add(values[2]);
                    if (monthIndex != null) {
                        monthTotals[monthIndex][1] += fileAdditions;
                        monthTotals[monthIndex][2] += fileDeletions;
                    }
                }
            }
        }

        List<GitMonthStatistics> months = new ArrayList<>();
        for (int index = 0; index < monthValues.size(); index += 1) {
            months.add(new GitMonthStatistics(
                    monthValues.get(index).toString(),
                    monthTotals[index][0],
                    monthTotals[index][1],
                    monthTotals[index][2]));
        }
        List<GitHourStatistics> hours = new ArrayList<>();
        for (int hour = 0; hour < hourTotals.length; hour += 1) {
            hours.add(new GitHourStatistics(hour, hourTotals[hour]));
        }
        List<GitAuthorStatistics> authorRanking = authors.values().stream()
                .map(AuthorAccumulator::toStatistics)
                .sorted(Comparator
                        .comparingLong(GitAuthorStatistics::commits)
                        .reversed()
                        .thenComparing(
                                GitAuthorStatistics::name,
                                String.CASE_INSENSITIVE_ORDER))
                .limit(20)
                .toList();
        CodeVolume codeVolume = codeVolume(rootPath);
        return new GitRepositoryStatistics(
                since.toString(),
                until.toString(),
                totalCommits,
                authors.size(),
                changedFiles.size(),
                additions,
                deletions,
                additions - deletions,
                codeVolume.lines(),
                codeVolume.trackedFiles(),
                List.copyOf(months),
                List.copyOf(hours),
                authorRanking);
    }

    @Override
    public GitReviewContext reviewContext(
            String path,
            String branch,
            String baseBranch,
            String fromCommit) {
        return reviewContext(
                path,
                branch,
                baseBranch,
                fromCommit,
                "",
                "");
    }

    @Override
    public GitReviewContext reviewContext(
            String path,
            String branch,
            String baseBranch,
            String fromCommit,
            String since,
            String until) {
        String rootPath = repositoryRoot(path);
        String headCommit = resolveCommit(
                rootPath,
                branch,
                "评审分支不存在");
        String baseHeadCommit = resolveCommit(
                rootPath,
                baseBranch,
                "对比分支不存在");
        String mergeBase = mergeBase(
                rootPath,
                baseHeadCommit,
                headCommit);
        String startCommit = fromCommit == null || fromCommit.isBlank()
                ? mergeBase
                : resolveCommit(
                        rootPath,
                        fromCommit,
                        "评审起始提交不存在");
        CommandResult ancestor = run(
                rootPath,
                "merge-base",
                "--is-ancestor",
                startCommit,
                headCommit);
        if (ancestor.exitCode() != 0) {
            throw new DomainException(
                    "评审起始提交不在当前分支历史中");
        }

        boolean dateFiltered = !since.isBlank() || !until.isBlank();
        List<String> rangeCommits = List.of();
        String rangeStart = startCommit;
        String rangeHead = headCommit;
        if (dateFiltered) {
            rangeCommits = reviewRangeCommits(
                    rootPath,
                    startCommit,
                    headCommit,
                    since,
                    until);
            if (rangeCommits.isEmpty()) {
                return new GitReviewContext(
                        branch,
                        baseBranch,
                        mergeBase,
                        headCommit,
                        startCommit,
                        0,
                        0,
                        0,
                        0,
                        List.of(),
                        List.of());
            }
            rangeStart = parentCommit(
                    rootPath,
                    rangeCommits.get(0),
                    startCommit);
            rangeHead = rangeCommits.get(rangeCommits.size() - 1);
        }

        int commitCount;
        if (dateFiltered) {
            commitCount = rangeCommits.size();
        } else {
            CommandResult countResult = run(
                    rootPath,
                    "rev-list",
                    "--count",
                    startCommit + ".." + headCommit);
            if (countResult.exitCode() != 0) {
                throw new DomainException("无法统计评审提交数量");
            }
            try {
                commitCount = Integer.parseInt(
                        countResult.output().trim());
            } catch (NumberFormatException exception) {
                throw new DomainException("无法统计评审提交数量");
            }
        }

        CommandResult numstat = runWithin(
                rootPath,
                REVIEW_TIMEOUT_SECONDS,
                MAX_REVIEW_OUTPUT_BYTES,
                "-c",
                "core.quotepath=false",
                "diff",
                "--numstat",
                "--no-renames",
                rangeStart,
                rangeHead);
        if (numstat.exitCode() != 0 || numstat.truncated()) {
            throw new DomainException(
                    numstat.truncated()
                            ? "代码差异过大，请缩小评审范围"
                            : "无法计算代码评审差异");
        }
        DiffSummary diff = parseDiffSummary(numstat.output());

        CommandResult changedFileResult = runWithin(
                rootPath,
                REVIEW_TIMEOUT_SECONDS,
                MAX_REVIEW_OUTPUT_BYTES,
                "-c",
                "core.quotepath=false",
                "diff",
                "--name-only",
                "--no-renames",
                "-z",
                rangeStart,
                rangeHead);
        if (changedFileResult.exitCode() != 0
                || changedFileResult.truncated()) {
            throw new DomainException(
                    changedFileResult.truncated()
                            ? "代码差异文件过多，请缩小评审范围"
                            : "无法读取代码评审文件列表");
        }
        List<String> changedFiles =
                parseChangedFiles(changedFileResult.output());

        CommandResult commitLog = runWithin(
                rootPath,
                REVIEW_TIMEOUT_SECONDS,
                MAX_OUTPUT_BYTES,
                "-c",
                "core.quotepath=false",
                "log",
                "--max-count=200",
                "--no-show-signature",
                "--format=%H%x00%h%x00%P%x00%an%x00%ae%x00%aI%x00%s%x1e",
                rangeStart + ".." + rangeHead);
        if (commitLog.exitCode() != 0 || commitLog.truncated()) {
            throw new DomainException(
                    commitLog.truncated()
                            ? "分支提交过多，请缩小评审范围"
                            : "无法读取评审提交记录");
        }
        return new GitReviewContext(
                branch,
                baseBranch,
                mergeBase,
                rangeHead,
                rangeStart,
                commitCount,
                diff.fileCount(),
                diff.additions(),
                diff.deletions(),
                parseCommitLog(commitLog.output()),
                changedFiles);
    }

    private static List<String> reviewRangeCommits(
            String rootPath,
            String startCommit,
            String headCommit,
            String since,
            String until) {
        List<String> arguments = new ArrayList<>();
        arguments.add("rev-list");
        arguments.add("--reverse");
        if (!since.isBlank()) {
            arguments.add("--since=" + since + " 00:00:00");
        }
        if (!until.isBlank()) {
            arguments.add("--until=" + until + " 23:59:59");
        }
        arguments.add(startCommit + ".." + headCommit);
        CommandResult result = runWithin(
                rootPath,
                REVIEW_TIMEOUT_SECONDS,
                MAX_REVIEW_OUTPUT_BYTES,
                arguments.toArray(String[]::new));
        if (result.exitCode() != 0 || result.truncated()) {
            throw new DomainException(
                    result.truncated()
                            ? "评审时间范围内的提交过多，请缩小范围"
                            : "无法读取评审时间范围内的提交");
        }
        return result.output()
                .lines()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static String parentCommit(
            String rootPath,
            String commit,
            String fallback) {
        CommandResult result = run(
                rootPath,
                "rev-list",
                "--parents",
                "-n",
                "1",
                commit);
        if (result.exitCode() != 0) return fallback;
        String[] fields = result.output().trim().split("\\s+");
        return fields.length > 1 ? fields[1] : fallback;
    }

    @Override
    public GitFileContent fileContent(
            String path,
            String revision,
            String filePath,
            int line) {
        String rootPath = repositoryRoot(path);
        String commit = resolveCommit(
                rootPath,
                revision,
                "评审提交不存在");
        CommandResult result = runWithin(
                rootPath,
                REVIEW_TIMEOUT_SECONDS,
                MAX_FILE_CONTENT_OUTPUT_BYTES,
                "-c",
                "core.quotepath=false",
                "show",
                commit + ":" + filePath);
        if (result.exitCode() != 0) {
            throw new DomainException(
                    "无法读取文件内容，该文件可能已删除或路径发生变化");
        }
        if (result.truncated()) {
            throw new DomainException("文件过大，无法在客户端中预览");
        }
        if (result.output().indexOf('\0') >= 0) {
            throw new DomainException("暂不支持预览二进制文件");
        }

        List<String> allLines = splitFileLines(result.output());
        int totalLines = allLines.size();
        if (totalLines == 0) {
            return new GitFileContent(
                    commit,
                    filePath,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    false,
                    false);
        }

        int requestedLine = line >= 1 && line <= totalLines
                ? line
                : 0;
        int anchor = requestedLine > 0 ? requestedLine : 1;
        int startLine = requestedLine > 0
                ? Math.max(1, anchor - FILE_CONTEXT_LINES)
                : 1;
        int endLine = requestedLine > 0
                ? Math.min(totalLines, anchor + FILE_CONTEXT_LINES)
                : Math.min(totalLines, FILE_CONTEXT_LINES * 2 + 1);
        List<String> visibleLines = allLines.subList(
                startLine - 1,
                endLine);
        return new GitFileContent(
                commit,
                filePath,
                requestedLine,
                startLine,
                endLine,
                totalLines,
                List.copyOf(visibleLines),
                startLine > 1,
                endLine < totalLines);
    }

    @Override
    public GitOperationResult execute(
            String path,
            GitOperationCommand command) {
        String rootPath = repositoryRoot(path);
        CommandResult result;
        switch (command.type()) {
            case FETCH -> result = run(
                    rootPath,
                    120,
                    "fetch",
                    "--all",
                    "--prune");
            case PULL -> result = run(
                    rootPath,
                    120,
                    "pull",
                    "--ff-only");
            case PUSH -> result = push(rootPath, command);
            case CREATE_BRANCH -> {
                CommandResult valid = validateBranch(
                        rootPath,
                        command.branchName());
                if (valid.exitCode() != 0) {
                    return failure(rootPath, "分支名称无效", valid.output());
                }
                result = run(
                        rootPath,
                        "switch",
                        "-c",
                        command.branchName());
            }
            case SWITCH_BRANCH -> {
                CommandResult valid = validateBranch(
                        rootPath,
                        command.branchName());
                if (valid.exitCode() != 0) {
                    return failure(rootPath, "分支名称无效", valid.output());
                }
                result = run(
                        rootPath,
                        "switch",
                        command.branchName());
            }
            case DELETE_BRANCH -> result = run(
                    rootPath,
                    "branch",
                    command.force() ? "-D" : "-d",
                    command.branchName());
            case MERGE -> {
                CommandResult valid = validateBranch(
                        rootPath,
                        command.sourceBranch());
                if (valid.exitCode() != 0) {
                    return failure(rootPath, "分支名称无效", valid.output());
                }
                result = run(
                        rootPath,
                        120,
                        "merge",
                        "--no-edit",
                        command.sourceBranch());
            }
            case ABORT_MERGE -> result = run(
                    rootPath,
                    "merge",
                    "--abort");
            case CONTINUE_MERGE -> {
                CommandResult mergeHead = run(
                        rootPath,
                        "rev-parse",
                        "-q",
                        "--verify",
                        "MERGE_HEAD");
                if (mergeHead.exitCode() != 0) {
                    return failure(
                            rootPath,
                            "当前没有正在进行的合并",
                            "");
                }
                List<String> conflicts = conflictFiles(rootPath);
                CommandResult markerCheck = run(
                        rootPath,
                        "diff",
                        "--check");
                if (markerCheck.exitCode() != 0) {
                    return failure(
                            rootPath,
                            "仍有未解决的冲突标记，请先完成冲突处理",
                            markerCheck.output());
                }
                CommandResult stagedMarkerCheck = run(
                        rootPath,
                        "diff",
                        "--cached",
                        "--check");
                if (stagedMarkerCheck.exitCode() != 0) {
                    return failure(
                            rootPath,
                            "已暂存文件中仍有冲突标记，请先完成冲突处理",
                            stagedMarkerCheck.output());
                }
                List<String> addArguments = new ArrayList<>();
                addArguments.add("add");
                addArguments.add("--");
                addArguments.addAll(conflicts);
                CommandResult add = run(
                        rootPath,
                        addArguments.toArray(String[]::new));
                if (add.exitCode() != 0) {
                    return failure(
                            rootPath,
                            "无法暂存已解决的冲突文件",
                            add.output());
                }
                CommandResult remainingStatus = run(
                        rootPath,
                        "-c",
                        "core.quotepath=false",
                        "status",
                        "--porcelain=v1",
                        "-z");
                if (remainingStatus.exitCode() != 0) {
                    return failure(
                            rootPath,
                            "无法确认冲突文件的暂存状态",
                            remainingStatus.output());
                }
                StatusSummary remaining = parseStatus(
                        remainingStatus.output());
                if (remaining.conflicted() > 0
                        || remaining.unstaged() > 0) {
                    return failure(
                            rootPath,
                            "仍有未暂存变更，请先处理后再继续合并",
                            remainingStatus.output());
                }
                result = run(
                        rootPath,
                        "commit",
                        "--no-edit");
            }
            case STAGE_ALL -> result = run(rootPath, "add", "-A");
            case COMMIT -> result = run(
                    rootPath,
                    "commit",
                    "-m",
                    command.commitMessage());
            default -> throw new DomainException("不支持的 Git 操作");
        }

        if (result.exitCode() != 0) {
            return failure(
                    rootPath,
                    operationFailureMessage(command.type()),
                    result.output());
        }
        return new GitOperationResult(
                true,
                operationSuccessMessage(command.type()),
                trimOutput(result.output()),
                currentBranch(rootPath),
                mergeInProgress(rootPath),
                conflictFiles(rootPath));
    }

    private static String repositoryRoot(String path) {
        Path directory;
        try {
            directory = Path.of(path).toRealPath();
        } catch (Exception exception) {
            throw new DomainException("Git 仓库目录不存在或无法访问", exception);
        }
        if (!Files.isDirectory(directory)) {
            throw new DomainException("Git 仓库路径必须是目录");
        }

        CommandResult result = runWithin(
                directory.toString(),
                COMMAND_TIMEOUT_SECONDS,
                "rev-parse",
                "--show-toplevel");
        if (result.exitCode() != 0) {
            throw new DomainException("所选目录不是 Git 仓库");
        }
        String root = firstNonBlankLine(result.output());
        if (root.isBlank()) {
            throw new DomainException("无法定位 Git 仓库根目录");
        }
        try {
            return Path.of(root).toRealPath().toString();
        } catch (Exception exception) {
            throw new DomainException("无法访问 Git 仓库根目录", exception);
        }
    }

    private static CommandResult push(
            String rootPath,
            GitOperationCommand command) {
        CommandResult remotes = run(rootPath, "remote");
        List<String> names = remotes.exitCode() == 0
                ? remotes.output().lines()
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList()
                : List.of();
        if (names.isEmpty()) {
            return new CommandResult(
                    1,
                    "当前仓库未配置远端",
                    false);
        }
        String remote = names.contains("origin") ? "origin" : names.get(0);
        String branch = command.branchName();
        if (command.setUpstream()) {
            CommandResult valid = validateBranch(rootPath, branch);
            if (valid.exitCode() != 0) {
                return valid;
            }
            return run(
                    rootPath,
                    120,
                    "push",
                    "--set-upstream",
                    remote,
                    branch);
        }

        CommandResult upstream = run(
                rootPath,
                "rev-parse",
                "--abbrev-ref",
                "--symbolic-full-name",
                "@{upstream}");
        if (upstream.exitCode() != 0) {
            return new CommandResult(
                    1,
                    "当前分支尚未设置远端跟踪分支，请使用“首次推送并设置上游”",
                    false);
        }
        return run(
                rootPath,
                120,
                "push");
    }

    private static CommandResult validateBranch(
            String rootPath,
            String branchName) {
        return run(
                rootPath,
                "check-ref-format",
                "--branch",
                branchName);
    }

    private static GitOperationResult failure(
            String rootPath,
            String message,
            String output) {
        return new GitOperationResult(
                false,
                message,
                trimOutput(output),
                currentBranch(rootPath),
                mergeInProgress(rootPath),
                conflictFiles(rootPath));
    }

    private static List<String> conflictFiles(String rootPath) {
        CommandResult result = run(
                rootPath,
                "-c",
                "core.quotepath=false",
                "status",
                "--porcelain=v1",
                "-z");
        if (result.exitCode() != 0) return List.of();
        return parseStatus(result.output()).conflictFiles();
    }

    private static String operationFailureMessage(GitOperationType type) {
        return switch (type) {
            case FETCH -> "拉取远端分支失败";
            case PULL -> "拉取提交失败；当前分支可能已分叉或存在本地变更";
            case PUSH -> "推送提交失败";
            case CREATE_BRANCH -> "创建分支失败";
            case SWITCH_BRANCH -> "切换分支失败；请先提交或清理工作区变更";
            case DELETE_BRANCH -> "删除分支失败；分支可能未合并或被占用";
            case MERGE -> "合并分支失败；请检查冲突和本地变更";
            case ABORT_MERGE -> "中止合并失败";
            case CONTINUE_MERGE -> "继续合并失败";
            case STAGE_ALL -> "暂存工作区变更失败";
            case COMMIT -> "提交失败；请检查暂存内容和 Git 用户配置";
        };
    }

    private static String operationSuccessMessage(GitOperationType type) {
        return switch (type) {
            case FETCH -> "已拉取全部远端分支";
            case PULL -> "已拉取最新提交";
            case PUSH -> "已推送当前分支";
            case CREATE_BRANCH -> "分支已创建并切换";
            case SWITCH_BRANCH -> "分支已切换";
            case DELETE_BRANCH -> "分支已删除";
            case MERGE -> "分支已合并";
            case ABORT_MERGE -> "合并已中止";
            case CONTINUE_MERGE -> "冲突已提交，合并完成";
            case STAGE_ALL -> "工作区变更已暂存";
            case COMMIT -> "提交已创建";
        };
    }

    private static String currentBranch(String rootPath) {
        CommandResult result = run(
                rootPath,
                "branch",
                "--show-current");
        String branch = firstNonBlankLine(result.output());
        return result.exitCode() == 0 && !branch.isBlank()
                ? branch
                : "detached";
    }

    private static boolean mergeInProgress(String rootPath) {
        CommandResult result = run(
                rootPath,
                "rev-parse",
                "-q",
                "--verify",
                "MERGE_HEAD");
        return result.exitCode() == 0;
    }

    private static String trimOutput(String output) {
        if (output == null) return "";
        return output.strip();
    }

    private static long parseStat(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) return 0;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static List<String> parseChangedFiles(String output) {
        return java.util.Arrays.stream(output.split("\u0000", -1))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .limit(5000)
                .toList();
    }

    private static CodeVolume codeVolume(String rootPath) {
        CommandResult result = runWithin(
                rootPath,
                STATISTICS_TIMEOUT_SECONDS,
                MAX_STATISTICS_OUTPUT_BYTES,
                "-c",
                "core.quotepath=false",
                "ls-files",
                "-z");
        if (result.exitCode() != 0 || result.truncated()) {
            throw new DomainException("无法读取 Git 跟踪文件");
        }
        Path root = Path.of(rootPath);
        long lines = 0;
        int trackedFiles = 0;
        for (String filename : result.output().split("\u0000", -1)) {
            if (filename.isEmpty()) continue;
            trackedFiles += 1;
            Path file = root.resolve(filename).normalize();
            if (!file.startsWith(root)
                    || !Files.isRegularFile(
                            file,
                            LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            try {
                if (Files.size(file) > MAX_TRACKED_FILE_BYTES) continue;
                long count = textLineCount(file);
                if (count >= 0) lines += count;
            } catch (IOException ignored) {
                // Unreadable or disappearing working tree files are skipped.
            }
        }
        return new CodeVolume(trackedFiles, lines);
    }

    private static long textLineCount(Path file) {
        long lines = 0;
        int last = -1;
        boolean hasContent = false;
        byte[] buffer = new byte[8192];
        try (InputStream input = Files.newInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                for (int index = 0; index < read; index += 1) {
                    int value = buffer[index] & 0xff;
                    if (value == 0) return -1;
                    if (!Character.isWhitespace((char) value)) {
                        hasContent = true;
                    }
                    if (value == '\n') lines += 1;
                    last = value;
                }
            }
        } catch (IOException ignored) {
            return -1;
        }
        if (!hasContent) return 0;
        return last == '\n' ? lines : lines + 1;
    }

    private static StatusSummary parseStatus(String output) {
        String branch = "HEAD";
        int ahead = 0;
        int behind = 0;
        int staged = 0;
        int unstaged = 0;
        int untracked = 0;
        int conflicted = 0;
        List<GitChangedFile> changedFiles = new ArrayList<>();
        List<String> conflictFiles = new ArrayList<>();

        boolean nulSeparated = output.indexOf('\u0000') >= 0;
        String[] records = nulSeparated
                ? output.split("\u0000", -1)
                : output.split("\\R");
        for (int index = 0; index < records.length; index += 1) {
            String line = records[index];
            if (line.startsWith("## ")) {
                BranchInfo parsed = parseBranch(line.substring(3));
                branch = parsed.name();
                ahead = parsed.ahead();
                behind = parsed.behind();
                continue;
            }
            if (line.length() < 2) continue;
            String code = line.substring(0, 2);
            String path = statusPath(line);
            if ("??".equals(code)) {
                untracked += 1;
                changedFiles.add(new GitChangedFile(
                        path,
                        code.substring(0, 1),
                        code.substring(1, 2),
                        false,
                        false,
                        true,
                        false));
                continue;
            }
            if ("!!".equals(code)) continue;
            if (nulSeparated
                    && (code.charAt(0) == 'R' || code.charAt(0) == 'C')) {
                index += 1;
            }
            if (CONFLICT_CODES.contains(code)) {
                conflicted += 1;
                conflictFiles.add(path);
                changedFiles.add(new GitChangedFile(
                        path,
                        code.substring(0, 1),
                        code.substring(1, 2),
                        false,
                        false,
                        false,
                        true));
                continue;
            }
            boolean isStaged = code.charAt(0) != ' ';
            boolean isUnstaged = code.charAt(1) != ' ';
            if (isStaged) staged += 1;
            if (isUnstaged) unstaged += 1;
            if (isStaged || isUnstaged) {
                changedFiles.add(new GitChangedFile(
                        path,
                        code.substring(0, 1),
                        code.substring(1, 2),
                        isStaged,
                        isUnstaged,
                        false,
                        false));
            }
        }

        return new StatusSummary(
                branch,
                ahead,
                behind,
                staged,
                unstaged,
                untracked,
                conflicted,
                List.copyOf(changedFiles),
                List.copyOf(conflictFiles));
    }

    private static BranchInfo parseBranch(String header) {
        String name;
        if (header.startsWith("No commits yet on ")) {
            name = header.substring("No commits yet on ".length()).trim();
        } else {
            int separator = header.indexOf("...");
            String branchPart = separator >= 0
                    ? header.substring(0, separator)
                    : header.split(" ", 2)[0];
            name = "HEAD (no branch)".equals(branchPart)
                    ? "detached"
                    : branchPart;
        }
        return new BranchInfo(
                name.isBlank() ? "HEAD" : name,
                matchCount(AHEAD_PATTERN, header),
                matchCount(BEHIND_PATTERN, header));
    }

    private static String statusPath(String line) {
        if (line.length() <= 3) return "";
        String value = line.substring(3).trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        int renameSeparator = value.indexOf(" -> ");
        return renameSeparator >= 0
                ? value.substring(renameSeparator + 4)
                : value;
    }

    private static int matchCount(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private static RemoteInfo remote(String rootPath) {
        CommandResult result = run(rootPath, "remote");
        if (result.exitCode() != 0) return RemoteInfo.EMPTY;
        List<String> remotes = result.output().lines()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        if (remotes.isEmpty()) return RemoteInfo.EMPTY;
        String name = remotes.contains("origin") ? "origin" : remotes.get(0);
        CommandResult urlResult = run(
                rootPath,
                "remote",
                "get-url",
                name);
        String url = urlResult.exitCode() == 0
                ? sanitizeRemoteUrl(firstNonBlankLine(urlResult.output()))
                : "";
        return new RemoteInfo(name, url);
    }

    private static String sanitizeRemoteUrl(String value) {
        if (value.isBlank()) return "";
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null) return value;
            if (!scheme.equalsIgnoreCase("http")
                    && !scheme.equalsIgnoreCase("https")
                    && !scheme.equalsIgnoreCase("ssh")) {
                return value;
            }
            if (uri.getHost() == null) return value;
            return new URI(
                    scheme,
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    null,
                    null).toString();
        } catch (Exception ignored) {
            return value;
        }
    }

    private static CommitInfo latestCommit(String rootPath) {
        CommandResult result = run(
                rootPath,
                "log",
                "-1",
                "--no-show-signature",
                "--format=%H%x00%h%x00%s%x00%cI");
        if (result.exitCode() != 0) return CommitInfo.EMPTY;
        String[] values = result.output().trim().split("\u0000", -1);
        return new CommitInfo(
                values.length > 0 ? values[0] : "",
                values.length > 1 ? values[1] : "",
                values.length > 2 ? values[2] : "",
                values.length > 3 ? values[3] : "");
    }

    private static String firstNonBlankLine(String output) {
        return output.lines()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .findFirst()
                .orElse("");
    }

    private static CommandResult run(String directory, String... arguments) {
        return run(
                directory,
                COMMAND_TIMEOUT_SECONDS,
                arguments);
    }

    private static CommandResult run(
            String directory,
            int timeoutSeconds,
            String... arguments) {
        return runWithin(directory, timeoutSeconds, arguments);
    }

    private static CommandResult runWithin(
            String directory,
            int timeoutSeconds,
            String... arguments) {
        return runWithin(
                directory,
                timeoutSeconds,
                MAX_OUTPUT_BYTES,
                arguments);
    }

    private static CommandResult runWithin(
            String directory,
            int timeoutSeconds,
            int maxOutputBytes,
            String... arguments) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(directory);
        command.addAll(List.of(arguments));

        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectErrorStream(true);
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("GIT_OPTIONAL_LOCKS", "0");
        builder.environment().put("LC_ALL", "C");

        Process process;
        try {
            process = builder.start();
        } catch (IOException exception) {
            throw new DomainException(
                    "未找到 Git 命令，请先安装 Git",
                    exception);
        }

        AtomicReference<CollectedOutput> outputRef = new AtomicReference<>();
        Thread reader = new Thread(
                () -> outputRef.set(readLimited(
                        process.getInputStream(),
                        maxOutputBytes)),
                "workcove-git-reader");
        reader.setDaemon(true);
        reader.start();

        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new DomainException("Git 命令执行超时");
            }
            reader.join();
            CollectedOutput output = outputRef.get();
            return new CommandResult(
                    process.exitValue(),
                    output == null ? "" : output.value(),
                    output != null && output.truncated());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new DomainException("Git 命令执行已中断", exception);
        }
    }

    private static CollectedOutput readLimited(
            InputStream input,
            int maxOutputBytes) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        boolean truncated = false;
        int total = 0;
        try (input) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                int remaining = maxOutputBytes - total;
                if (remaining > 0) {
                    int accepted = Math.min(remaining, read);
                    output.write(buffer, 0, accepted);
                    total += accepted;
                }
                if (read > remaining) truncated = true;
            }
        } catch (IOException ignored) {
        }
        return new CollectedOutput(
                output.toString(StandardCharsets.UTF_8),
                truncated);
    }

    private record BranchInfo(String name, int ahead, int behind) {
    }

    private record StatusSummary(
            String branch,
            int ahead,
            int behind,
            int staged,
            int unstaged,
            int untracked,
            int conflicted,
            List<GitChangedFile> changedFiles,
            List<String> conflictFiles) {
    }

    private record DiffSummary(
            int fileCount,
            long additions,
            long deletions) {
    }

    private record RemoteInfo(String name, String url) {
        private static final RemoteInfo EMPTY = new RemoteInfo("", "");
    }

    private record CommitInfo(
            String hash,
            String shortHash,
            String subject,
            String committedAt) {
        private static final CommitInfo EMPTY =
                new CommitInfo("", "", "", "");
    }

    private record CommandResult(
            int exitCode,
            String output,
            boolean truncated) {
    }

    private record CollectedOutput(String value, boolean truncated) {
    }

    private record CodeVolume(int trackedFiles, long lines) {
    }

    private static final class AuthorAccumulator {
        private final String name;
        private final String email;
        private long commits;
        private long additions;
        private long deletions;

        private AuthorAccumulator(String name, String email) {
            this.name = name.isBlank() ? "未知作者" : name.trim();
            this.email = email;
        }

        private GitAuthorStatistics toStatistics() {
            return new GitAuthorStatistics(
                    name,
                    email,
                    commits,
                    additions,
                    deletions);
        }
    }
}
