package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.development.domain.model.GitBranchInfo;
import com.pydlove.workcove.development.domain.model.GitOperationCommand;
import com.pydlove.workcove.development.domain.model.GitOperationResult;
import com.pydlove.workcove.development.domain.model.GitOperationType;
import com.pydlove.workcove.development.domain.model.GitRepositoryStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(directory.toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        assertTrue(
                process.waitFor(10, TimeUnit.SECONDS),
                () -> "Git command timed out: " + command);
        String output = new String(
                process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
    }
}
