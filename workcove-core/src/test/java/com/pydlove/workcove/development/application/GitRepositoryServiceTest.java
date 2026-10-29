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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitRepositoryServiceTest {

    private final FakeGateway gateway = new FakeGateway();
    private final GitRepositoryService service =
            new GitRepositoryService(gateway);

    @Test
    void normalizesAnAbsolutePathBeforeInspection() {
        GitRepositoryStatus result = service.inspect("/tmp/../tmp/project/");

        assertEquals("/tmp/project", gateway.lastPath);
        assertEquals("/tmp/project", result.rootPath());
    }

    @Test
    void rejectsRelativeOrInvalidPaths() {
        assertThrows(
                DomainException.class,
                () -> service.inspect("project"));
        assertThrows(
                DomainException.class,
                () -> service.inspect(" "));
        assertThrows(
                DomainException.class,
                () -> service.inspect("/tmp/project\n--help"));
        assertEquals(0, gateway.calls);
    }

    @Test
    void validatesBranchNamesAndLogLimitsBeforeCallingGateway() {
        assertThrows(
                DomainException.class,
                () -> service.execute(
                        "/tmp/project",
                        new GitOperationCommand(
                                GitOperationType.SWITCH_BRANCH,
                                "--detach",
                                "",
                                "",
                                false,
                                false)));
        assertThrows(
                DomainException.class,
                () -> service.log("/tmp/project", 0));
        assertThrows(
                DomainException.class,
                () -> service.log("/tmp/project", 201));
        assertThrows(
                DomainException.class,
                () -> service.authorCommits("/tmp/project", " ", ""));
        assertEquals(0, gateway.calls);
    }

    @Test
    void normalizesThePathBeforeStatistics() {
        GitRepositoryStatistics statistics = service.statistics(
                "/tmp/../tmp/project/");

        assertEquals("/tmp/project", gateway.lastPath);
        assertEquals(0, statistics.totalCommits());
    }

    @Test
    void normalizesAuthorDetailsBeforeLoadingCommits() {
        List<GitCommitInfo> commits = service.authorCommits(
                "/tmp/../tmp/project/",
                " Alice ",
                " alice@example.com ");

        assertEquals(List.of(), commits);
        assertEquals("/tmp/project", gateway.lastPath);
        assertEquals("Alice", gateway.lastAuthorName);
        assertEquals("alice@example.com", gateway.lastAuthorEmail);
    }

    @Test
    void validatesAndNormalizesFileContentRequests() {
        GitFileContent content = service.fileContent(
                "/tmp/../tmp/project/",
                "ABCDEF12",
                " src/main/App.java ",
                42);

        assertEquals("/tmp/project", gateway.lastPath);
        assertEquals("abcdef12", gateway.lastRevision);
        assertEquals("src/main/App.java", gateway.lastFilePath);
        assertEquals(42, gateway.lastFileLine);
        assertEquals(42, content.requestedLine());
        assertThrows(
                DomainException.class,
                () -> service.fileContent(
                        "/tmp/project",
                        "not-a-commit",
                        "src/main/App.java",
                        1));
        assertThrows(
                DomainException.class,
                () -> service.fileContent(
                        "/tmp/project",
                        "abcdef12",
                        "../secret.txt",
                        1));
    }

    @Test
    void passesNormalizedReviewDateRangeToTheGateway() {
        service.reviewContext(
                "/tmp/../tmp/project/",
                "feature",
                "develop",
                "",
                " 2026-10-29 ",
                "2026-10-30");

        assertEquals("2026-10-29", gateway.lastSince);
        assertEquals("2026-10-30", gateway.lastUntil);
    }

    @Test
    void rejectsInvalidOrReversedReviewDates() {
        assertThrows(
                DomainException.class,
                () -> service.reviewContext(
                        "/tmp/project",
                        "feature",
                        "develop",
                        "",
                        "2026-10-30",
                        "2026-10-29"));
        assertThrows(
                DomainException.class,
                () -> service.reviewContext(
                        "/tmp/project",
                        "feature",
                        "develop",
                        "",
                        "not-a-date",
                        "2026-10-29"));
    }

    private static final class FakeGateway implements GitRepositoryGateway {

        private String lastPath = "";
        private String lastAuthorName = "";
        private String lastAuthorEmail = "";
        private String lastRevision = "";
        private String lastFilePath = "";
        private String lastSince = "";
        private String lastUntil = "";
        private int lastFileLine;
        private int calls;

        @Override
        public GitRepositoryStatus inspect(String path) {
            lastPath = path;
            calls += 1;
            return new GitRepositoryStatus(
                    path,
                    "project",
                    "main",
                    "origin",
                    "https://example.com/project.git",
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "",
                    "",
                    "",
                    "",
                    false,
                    List.of(),
                    List.of());
        }

        @Override
        public List<GitBranchInfo> branches(String path) {
            lastPath = path;
            calls += 1;
            return List.of();
        }

        @Override
        public List<GitCommitInfo> log(String path, int limit) {
            lastPath = path;
            calls += 1;
            return List.of();
        }

        @Override
        public List<GitCommitInfo> authorCommits(
                String path,
                String authorName,
                String authorEmail) {
            lastPath = path;
            lastAuthorName = authorName;
            lastAuthorEmail = authorEmail;
            calls += 1;
            return List.of();
        }

        @Override
        public GitRepositoryStatistics statistics(String path) {
            lastPath = path;
            calls += 1;
            return new GitRepositoryStatistics(
                    "",
                    "",
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    List.of(),
                    List.of());
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
            lastPath = path;
            lastSince = since;
            lastUntil = until;
            calls += 1;
            return new GitReviewContext(
                    branch,
                    baseBranch,
                    "",
                    "",
                    fromCommit,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    List.of());
        }

        @Override
        public GitFileContent fileContent(
                String path,
                String revision,
                String filePath,
                int line) {
            lastPath = path;
            lastRevision = revision;
            lastFilePath = filePath;
            lastFileLine = line;
            calls += 1;
            return new GitFileContent(
                    revision,
                    filePath,
                    line,
                    line,
                    line,
                    line,
                    List.of(),
                    false,
                    false);
        }

        @Override
        public GitOperationResult execute(
                String path,
                GitOperationCommand command) {
            lastPath = path;
            calls += 1;
            return new GitOperationResult(
                    true,
                    "",
                    "",
                    "main",
                    false,
                    List.of());
        }
    }
}
