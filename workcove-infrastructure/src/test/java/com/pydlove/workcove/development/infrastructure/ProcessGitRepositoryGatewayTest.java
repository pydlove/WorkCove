package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.development.domain.model.GitBranchInfo;
import com.pydlove.workcove.development.domain.model.GitOperationCommand;
import com.pydlove.workcove.development.domain.model.GitOperationResult;
import com.pydlove.workcove.development.domain.model.GitOperationType;
import com.pydlove.workcove.development.domain.model.GitFileContent;
import com.pydlove.workcove.development.domain.model.GitRepositoryStatistics;
import com.pydlove.workcove.development.domain.model.GitRepositoryStatus;
import com.pydlove.workcove.development.domain.model.GitReviewContext;
import com.pydlove.workcove.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessGitRepositoryGatewayTest {

    @TempDir
    Path temporaryDirectory;

    private final ProcessGitRepositoryGateway gateway =
            new ProcessGitRepositoryGateway();

    @Test
    void inspectsBranchRemoteAndWorkingTreeState() throws Exception {
        initializeRepository();
        Files.writeString(temporaryDirectory.resolve("tracked.txt"), "initial");
        runGit(temporaryDirectory, "add", "tracked.txt");
        runGit(temporaryDirectory, "commit", "-m", "Initial commit");
        runGit(
                temporaryDirectory,
                "remote",
                "add",
                "origin",
                "https://secret-token@example.com/group/project.git?access_token=secret");

        Files.writeString(temporaryDirectory.resolve("tracked.txt"), "changed");
        Files.writeString(temporaryDirectory.resolve("staged.txt"), "staged");
        runGit(temporaryDirectory, "add", "staged.txt");
        Files.writeString(temporaryDirectory.resolve("untracked.txt"), "new");

        GitRepositoryStatus status = gateway.inspect(
                temporaryDirectory.toString());

        assertEquals(
                temporaryDirectory.toRealPath().toString(),
                status.rootPath());
        assertFalse(status.branch().isBlank());
        assertEquals("origin", status.remoteName());
        assertEquals(
                "https://example.com/group/project.git",
                status.remoteUrl());
        assertEquals(1, status.staged());
        assertEquals(1, status.unstaged());
        assertEquals(1, status.untracked());
        assertEquals(0, status.conflicted());
        assertEquals(
                List.of("staged.txt", "tracked.txt", "untracked.txt"),
                status.changedFiles().stream()
                        .map(file -> file.path())
                        .sorted()
                        .toList());
        assertTrue(status.changedFiles().stream()
                .anyMatch(file -> file.path().equals("untracked.txt")
                        && file.untracked()));
        assertEquals("Initial commit", status.latestCommitSubject());
        assertFalse(status.latestCommitShortHash().isBlank());
        assertFalse(status.clean());
    }

    @Test
    void listsAndManagesBranchesCommitsAndHistory() throws Exception {
        initializeRepository();
        Files.writeString(temporaryDirectory.resolve("tracked.txt"), "initial");
        runGit(temporaryDirectory, "add", "tracked.txt");
        runGit(temporaryDirectory, "commit", "-m", "Initial commit");

        List<GitBranchInfo> initialBranches = gateway.branches(
                temporaryDirectory.toString());
        assertEquals(1, initialBranches.size());
        assertEquals("main", initialBranches.get(0).name());
        assertTrue(initialBranches.get(0).current());

        GitOperationResult created = gateway.execute(
                temporaryDirectory.toString(),
                command(GitOperationType.CREATE_BRANCH, "feature"));
        assertTrue(created.success());
        assertEquals("feature", created.currentBranch());

        assertEquals(
                1,
                gateway.log(temporaryDirectory.toString(), 20).size());
        assertEquals(
                "Initial commit",
                gateway.log(temporaryDirectory.toString(), 20)
                        .get(0)
                        .subject());

        Files.writeString(temporaryDirectory.resolve("tracked.txt"), "feature");
        runGit(temporaryDirectory, "add", "tracked.txt");
        runGit(temporaryDirectory, "commit", "-m", "Feature commit");

        assertTrue(gateway.execute(
                temporaryDirectory.toString(),
                command(GitOperationType.SWITCH_BRANCH, "main")).success());
        assertFalse(gateway.execute(
                temporaryDirectory.toString(),
                command(GitOperationType.DELETE_BRANCH, "feature")).success());
        assertTrue(gateway.execute(
                temporaryDirectory.toString(),
                new GitOperationCommand(
                        GitOperationType.DELETE_BRANCH,
                        "feature",
                        "",
                        "",
                        true,
                        false)).success());
        assertTrue(gateway.branches(temporaryDirectory.toString()).stream()
                .noneMatch(branch -> branch.name().equals("feature")));

        Files.writeString(temporaryDirectory.resolve("staged.txt"), "staged");
        assertTrue(gateway.execute(
                temporaryDirectory.toString(),
                command(GitOperationType.STAGE_ALL, "")).success());
        GitOperationResult committed = gateway.execute(
                temporaryDirectory.toString(),
                new GitOperationCommand(
                        GitOperationType.COMMIT,
                        "",
                        "",
                        "Main commit",
                        false,
                        false));
        assertTrue(committed.success());
        assertEquals("Main commit", gateway.inspect(
                temporaryDirectory.toString()).latestCommitSubject());
    }

    @Test
    void reportsMergeConflictsAndSupportsAbortOrContinue()
            throws Exception {
        initializeRepository();
        String conflictFile = "冲突 文件.txt";
        Files.writeString(temporaryDirectory.resolve(conflictFile), "base\n");
        runGit(temporaryDirectory, "add", conflictFile);
        runGit(temporaryDirectory, "commit", "-m", "Base");

        assertTrue(gateway.execute(
                temporaryDirectory.toString(),
                command(GitOperationType.CREATE_BRANCH, "feature")).success());
        Files.writeString(
                temporaryDirectory.resolve(conflictFile),
                "feature\n");
        runGit(temporaryDirectory, "add", conflictFile);
        runGit(temporaryDirectory, "commit", "-m", "Feature change");

        assertTrue(gateway.execute(
                temporaryDirectory.toString(),
                command(GitOperationType.SWITCH_BRANCH, "main")).success());
        Files.writeString(
                temporaryDirectory.resolve(conflictFile),
                "main\n");
        runGit(temporaryDirectory, "add", conflictFile);
        runGit(temporaryDirectory, "commit", "-m", "Main change");

        GitOperationResult conflict = gateway.execute(
                temporaryDirectory.toString(),
                new GitOperationCommand(
                        GitOperationType.MERGE,
                        "",
                        "feature",
                        "",
                        false,
                        false));
        assertFalse(conflict.success());
        assertEquals(List.of(conflictFile), conflict.conflictFiles());
        assertEquals(1, gateway.inspect(
                temporaryDirectory.toString()).conflicted());

        GitOperationResult unresolved = gateway.execute(
                temporaryDirectory.toString(),
                command(GitOperationType.CONTINUE_MERGE, ""));
        assertFalse(unresolved.success());
        assertEquals(List.of(conflictFile), unresolved.conflictFiles());

        GitOperationResult aborted = gateway.execute(
                temporaryDirectory.toString(),
                command(GitOperationType.ABORT_MERGE, ""));
        assertTrue(aborted.success());
        assertTrue(aborted.conflictFiles().isEmpty());

        assertFalse(gateway.execute(
                temporaryDirectory.toString(),
                new GitOperationCommand(
                        GitOperationType.MERGE,
                        "",
                        "feature",
                        "",
                        false,
                        false)).success());
        runGit(temporaryDirectory, "add", conflictFile);
        assertFalse(gateway.execute(
                temporaryDirectory.toString(),
                command(GitOperationType.CONTINUE_MERGE, "")).success());
        Files.writeString(
                temporaryDirectory.resolve(conflictFile),
                "resolved\n");
        assertTrue(gateway.execute(
                temporaryDirectory.toString(),
                command(GitOperationType.STAGE_ALL, "")).success());
        GitOperationResult continued = gateway.execute(
                temporaryDirectory.toString(),
                command(GitOperationType.CONTINUE_MERGE, ""));
        assertTrue(continued.success());
        assertTrue(continued.conflictFiles().isEmpty());
    }

    @Test
    void filtersAuthorCommitsByExactEmail() throws Exception {
        initializeRepository();
        runGit(temporaryDirectory, "config", "user.name", "Alice");
        runGit(
                temporaryDirectory,
                "config",
                "user.email",
                "alice@example.com");
        Files.writeString(temporaryDirectory.resolve("alice.txt"), "alice\n");
        runGit(temporaryDirectory, "add", "alice.txt");
        runGit(temporaryDirectory, "commit", "-m", "Alice exact commit");

        runGit(temporaryDirectory, "config", "user.name", "Malice");
        runGit(
                temporaryDirectory,
                "config",
                "user.email",
                "malice@example.com");
        Files.writeString(temporaryDirectory.resolve("malice.txt"), "malice\n");
        runGit(temporaryDirectory, "add", "malice.txt");
        runGit(temporaryDirectory, "commit", "-m", "Malice commit");

        assertEquals(
                List.of("Alice exact commit"),
                gateway.authorCommits(
                                temporaryDirectory.toString(),
                                "Alice",
                                "alice@example.com")
                        .stream()
                        .map(commit -> commit.subject())
                        .toList());
    }

    @Test
    void buildsIncrementalReviewContextFromBranchMergeBase() throws Exception {
        initializeRepository();
        Files.writeString(temporaryDirectory.resolve("common.txt"), "base\n");
        runGit(temporaryDirectory, "add", "common.txt");
        runGit(temporaryDirectory, "commit", "-m", "Base");
        runGit(temporaryDirectory, "switch", "-c", "feature");

        Files.writeString(temporaryDirectory.resolve("feature.txt"), "one\n");
        runGit(temporaryDirectory, "add", "feature.txt");
        runGit(temporaryDirectory, "commit", "-m", "Feature file");
        String firstFeatureCommit = gateway.inspect(
                temporaryDirectory.toString()).latestCommitHash();

        Files.writeString(
                temporaryDirectory.resolve("common.txt"),
                "base\nfeature\n");
        runGit(temporaryDirectory, "add", "common.txt");
        runGit(temporaryDirectory, "commit", "-m", "Update common");

        GitReviewContext full = gateway.reviewContext(
                temporaryDirectory.toString(),
                "feature",
                "main",
                "");
        GitReviewContext incremental = gateway.reviewContext(
                temporaryDirectory.toString(),
                "feature",
                "main",
                firstFeatureCommit);

        assertEquals(2, full.commitCount());
        assertEquals(2, full.fileCount());
        assertEquals(2, full.additions());
        assertEquals(0, full.deletions());
        assertEquals(2, full.commits().size());
        assertEquals(
                List.of("common.txt", "feature.txt"),
                full.changedFiles());
        assertEquals(firstFeatureCommit, incremental.fromCommit());
        assertEquals(1, incremental.commitCount());
        assertEquals(1, incremental.fileCount());
        assertEquals(List.of("common.txt"), incremental.changedFiles());
    }

    @Test
    void filtersReviewContextByCommitDateRange() throws Exception {
        initializeRepository();
        runGitAt(
                temporaryDirectory,
                "2026-10-27T10:00:00+08:00",
                "commit",
                "--allow-empty",
                "-m",
                "Base");
        runGit(temporaryDirectory, "switch", "-c", "feature");

        Files.writeString(temporaryDirectory.resolve("yesterday.txt"), "one\n");
        runGit(temporaryDirectory, "add", "yesterday.txt");
        runGitAt(
                temporaryDirectory,
                "2026-10-28T10:00:00+08:00",
                "commit",
                "-m",
                "Yesterday");

        Files.writeString(temporaryDirectory.resolve("today.txt"), "two\n");
        runGit(temporaryDirectory, "add", "today.txt");
        runGitAt(
                temporaryDirectory,
                "2026-10-29T10:00:00+08:00",
                "commit",
                "-m",
                "Today");

        GitReviewContext today = gateway.reviewContext(
                temporaryDirectory.toString(),
                "feature",
                "main",
                "",
                "2026-10-29",
                "2026-10-29");
        GitReviewContext empty = gateway.reviewContext(
                temporaryDirectory.toString(),
                "feature",
                "main",
                "",
                "2026-10-30",
                "2026-10-30");

        assertEquals(1, today.commitCount());
        assertEquals(1, today.fileCount());
        assertEquals(1, today.additions());
        assertEquals(List.of("today.txt"), today.changedFiles());
        assertEquals(0, empty.commitCount());
        assertEquals(0, empty.fileCount());
    }

    @Test
    void readsFileContentAroundTheRequestedLine() throws Exception {
        initializeRepository();
        Path source = temporaryDirectory.resolve("src/main/Example.java");
        Files.createDirectories(source.getParent());
        StringBuilder value = new StringBuilder();
        for (int line = 1; line <= 300; line += 1) {
            value.append("line ").append(line).append('\n');
        }
        Files.writeString(source, value);
        runGit(temporaryDirectory, "add", ".");
        runGit(temporaryDirectory, "commit", "-m", "Add source");
        String commit = gateway.inspect(
                temporaryDirectory.toString()).latestCommitHash();

        GitFileContent content = gateway.fileContent(
                temporaryDirectory.toString(),
                commit,
                "src/main/Example.java",
                150);

        assertEquals(commit, content.revision());
        assertEquals("src/main/Example.java", content.filePath());
        assertEquals(150, content.requestedLine());
        assertEquals(70, content.startLine());
        assertEquals(230, content.endLine());
        assertEquals(300, content.totalLines());
        assertEquals("line 150", content.lines().get(80));
        assertTrue(content.truncatedBefore());
        assertTrue(content.truncatedAfter());
        assertThrows(
                DomainException.class,
                () -> gateway.fileContent(
                        temporaryDirectory.toString(),
                        commit,
                        "src/main/Missing.java",
                        1));
    }

    @Test
    void aggregatesYearlyGitStatistics() throws Exception {
        initializeRepository();
        runGit(temporaryDirectory, "config", "user.name", "Alice");
        runGit(
                temporaryDirectory,
                "config",
                "user.email",
                "alice@example.com");
        Files.writeString(
                temporaryDirectory.resolve("alice.txt"),
                "one\ntwo\n");
        runGit(temporaryDirectory, "add", "alice.txt");
        runGit(temporaryDirectory, "commit", "-m", "Alice commit");

        runGit(temporaryDirectory, "config", "user.name", "Bob");
        runGit(
                temporaryDirectory,
                "config",
                "user.email",
                "bob@example.com");
        Files.writeString(
                temporaryDirectory.resolve("bob.txt"),
                "three\n");
        runGit(temporaryDirectory, "add", "bob.txt");
        runGit(temporaryDirectory, "commit", "-m", "Bob commit");

        GitRepositoryStatistics statistics = gateway.statistics(
                temporaryDirectory.toString());

        assertEquals(2, statistics.totalCommits());
        assertEquals(2, statistics.activeAuthors());
        assertEquals(2, statistics.changedFiles());
        assertEquals(3, statistics.additions());
        assertEquals(0, statistics.deletions());
        assertEquals(3, statistics.totalLines());
        assertEquals(2, statistics.trackedFiles());
        assertEquals(12, statistics.months().size());
        assertEquals(24, statistics.hours().size());
        assertEquals(
                2,
                statistics.authors().stream()
                        .mapToLong(author -> author.commits())
                        .sum());
        assertEquals(
                3,
                statistics.authors().stream()
                        .mapToLong(author -> author.additions())
                        .sum());
        assertEquals(
                List.of("Alice commit"),
                gateway.authorCommits(
                                temporaryDirectory.toString(),
                                "Alice",
                                "alice@example.com")
                        .stream()
                        .map(commit -> commit.subject())
                        .toList());
        assertEquals(
                List.of("Bob commit"),
                gateway.authorCommits(
                                temporaryDirectory.toString(),
                                "Bob",
                                "bob@example.com")
                        .stream()
                        .map(commit -> commit.subject())
                        .toList());
    }

    private void initializeRepository() throws Exception {
        runGit(temporaryDirectory, "init", "-b", "main");
        runGit(temporaryDirectory, "config", "user.name", "WorkCove Test");
        runGit(
                temporaryDirectory,
                "config",
                "user.email",
                "workcove-test@example.com");
    }

    private static GitOperationCommand command(
            GitOperationType type,
            String branchName) {
        return new GitOperationCommand(
                type,
                branchName,
                "",
                "",
                false,
                false);
    }

    private static void runGit(Path directory, String... arguments)
            throws Exception {
        runGitAt(directory, null, arguments);
    }

    private static void runGitAt(
            Path directory,
            String date,
            String... arguments) throws Exception {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(directory.toString());
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectErrorStream(true);
        if (date != null) {
            builder.environment().put("GIT_AUTHOR_DATE", date);
            builder.environment().put("GIT_COMMITTER_DATE", date);
        }
        Process process = builder.start();
        assertTrue(
                process.waitFor(10, TimeUnit.SECONDS),
                () -> "Git command timed out: " + command);
        String output = new String(
                process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
    }
}
