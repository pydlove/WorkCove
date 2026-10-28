package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.domain.model.GitBranchInfo;
import com.pydlove.workcove.development.domain.model.GitCommitInfo;
import com.pydlove.workcove.development.domain.model.GitOperationCommand;
import com.pydlove.workcove.development.domain.model.GitOperationResult;
import com.pydlove.workcove.development.domain.model.GitOperationType;
import com.pydlove.workcove.development.domain.model.GitRepositoryStatus;
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
        assertEquals(0, gateway.calls);
    }

    private static final class FakeGateway implements GitRepositoryGateway {

        private String lastPath = "";
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
