package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.application.query.ZentaoSyncResult;
import com.pydlove.workcove.development.application.command.ZentaoBuildCreateCommand;
import com.pydlove.workcove.development.application.command.ZentaoTestAttachment;
import com.pydlove.workcove.development.application.command.ZentaoTestTaskCreateCommand;
import com.pydlove.workcove.development.domain.model.ZentaoBug;
import com.pydlove.workcove.development.domain.model.ZentaoBuild;
import com.pydlove.workcove.development.domain.model.ZentaoConnection;
import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.development.domain.port.ZentaoGateway;
import com.pydlove.workcove.development.domain.repository.ZentaoRepository;
import com.pydlove.workcove.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZentaoServiceTest {

    private static final long OWNER_ID = 10L;
    private static final String WORKSPACE_ID = "default";
    private static final Instant NOW =
            Instant.parse("2026-10-27T00:00:00Z");

    private final InMemoryRepository repository =
            new InMemoryRepository();
    private final FakeGateway gateway = new FakeGateway();
    private final ZentaoService service = new ZentaoService(
            repository,
            new PassThroughCipher(),
            gateway,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void synchronizesOnlySelectedProjects() {
        repository.connection = connection(List.of("1448"));

        ZentaoSyncResult result = service.sync(OWNER_ID, WORKSPACE_ID);

        assertEquals(1, result.projects());
        assertEquals(1, result.bugs());
        assertEquals(1, result.builds());
        assertEquals(List.of("1448"), gateway.loadedProjectIds);
        assertEquals("1448", repository.bugs.get(0).projectId());
        assertEquals("AI 安全网关", repository.bugs.get(0).projectName());
        assertEquals("v3.0.2-R100", repository.builds.get(0).name());
        assertEquals(1, service.builds(OWNER_ID, WORKSPACE_ID).size());
    }

    @Test
    void requiresAtLeastOneSelectedProject() {
        repository.connection = connection(List.of());

        assertThrows(
                DomainException.class,
                () -> service.sync(OWNER_ID, WORKSPACE_ID));
    }

    @Test
    void skipsClosedProjects() {
        repository.connection = connection(List.of("9999"));

        assertThrows(
                DomainException.class,
                () -> service.sync(OWNER_ID, WORKSPACE_ID));
        assertTrue(gateway.loadedProjectIds.isEmpty());
    }

    @Test
    void deletesBugsOnlyFromTheLocalProduct() {
        repository.connection = connection(List.of("1448"));
        service.sync(OWNER_ID, WORKSPACE_ID);
        ZentaoBug synced = repository.bugs.get(0);

        service.deleteBug(OWNER_ID, WORKSPACE_ID, synced.id());

        assertTrue(repository.bugs.get(0).localDeleted());
        assertTrue(service.bugs(OWNER_ID, WORKSPACE_ID).isEmpty());
    }

    @Test
    void doesNotRestoreLocallyDeletedBugsOnSync() {
        repository.connection = connection(List.of("1448"));
        repository.bugs.add(bug(true));

        ZentaoSyncResult result = service.sync(OWNER_ID, WORKSPACE_ID);

        assertEquals(0, result.bugs());
        assertTrue(repository.bugs.get(0).localDeleted());
    }

    @Test
    void keepsLocalResolutionWhenSynchronizingAgain() {
        repository.connection = connection(List.of("1448"));
        service.sync(OWNER_ID, WORKSPACE_ID);
        ZentaoBug synced = repository.bugs.get(0);

        service.resolveLocally(
                OWNER_ID,
                WORKSPACE_ID,
                synced.id(),
                "修复同步状态竞争",
                "并发同步覆盖了本地状态");
        service.sync(OWNER_ID, WORKSPACE_ID);

        assertTrue(repository.bugs.get(0).localResolved());
        assertEquals("resolved", repository.bugs.get(0).localStatus());
        assertEquals("修复同步状态竞争", repository.bugs.get(0).solutionText());
        assertEquals("并发同步覆盖了本地状态", repository.bugs.get(0).reasonText());
        assertTrue(service.bugs(OWNER_ID, WORKSPACE_ID).get(0).localResolved());
    }

    @Test
    void confirmsBugThroughZentaoAndUpdatesLocalState() {
        repository.connection = connection(List.of("1448"));
        service.sync(OWNER_ID, WORKSPACE_ID);
        ZentaoBug synced = repository.bugs.get(0);

        var confirmed = service.confirm(
                OWNER_ID,
                WORKSPACE_ID,
                synced.id());

        assertTrue(confirmed.confirmed());
        assertTrue(repository.bugs.get(0).confirmed());
        assertEquals("1024", gateway.confirmedBugId);
        assertEquals("yong", gateway.confirmedAssignedTo);
    }

    @Test
    void requiresSolutionAndReasonForLocalResolution() {
        repository.connection = connection(List.of("1448"));
        service.sync(OWNER_ID, WORKSPACE_ID);
        ZentaoBug synced = repository.bugs.get(0);

        assertThrows(
                DomainException.class,
                () -> service.resolveLocally(
                        OWNER_ID,
                        WORKSPACE_ID,
                        synced.id(),
                        "",
                        "缺少解决方案"));
        assertThrows(
                DomainException.class,
                () -> service.resolveLocally(
                        OWNER_ID,
                        WORKSPACE_ID,
                        synced.id(),
                        "已修复",
                        ""));
    }

    @Test
    void loadsBugMediaOnlyFromTheConfiguredConnection() {
        repository.connection = connection(List.of("1448"));
        repository.bugs.add(bug(false));

        var media = service.media(
                OWNER_ID,
                WORKSPACE_ID,
                1L,
                "file-read-1.png");

        assertEquals("image/png", media.contentType());
        assertEquals("file-read-1.png", gateway.mediaSource);
        assertThrows(
                DomainException.class,
                () -> service.media(
                        OWNER_ID,
                        "another-workspace",
                        1L,
                        "file-read-1.png"));
    }

    @Test
    void createsBuildWithTheSelectedProjectContext() {
        repository.connection = connection(List.of("1448"));

        var created = service.createBuild(
                OWNER_ID,
                WORKSPACE_ID,
                new ZentaoBuildCreateCommand(
                        "1448",
                        "1455",
                        "1",
                        "127",
                        "V3.0.2_R100B003",
                        "yong",
                        "2026-10-29",
                        "https://example.com/build.tar.gz"));

        assertEquals("900", created.remoteId());
        assertEquals("V3.0.2_R100B003", created.name());
        assertEquals("1455", gateway.createdBuild.executionId());
        assertEquals("127", gateway.createdBuild.systemId());
    }

    @Test
    void loadsBuildAndTestTaskOptionsFromTheConfiguredProject() {
        repository.connection = connection(List.of("1448"));

        var buildOptions = service.buildOptions(
                OWNER_ID,
                WORKSPACE_ID);
        var systems = service.systems(
                OWNER_ID,
                WORKSPACE_ID,
                "1");
        var testTaskOptions = service.testTaskOptions(
                OWNER_ID,
                WORKSPACE_ID,
                "1");

        assertEquals("1455", buildOptions.executions().get(0).id());
        assertEquals("1", buildOptions.executions().get(0).productId());
        assertTrue(buildOptions.products().stream().anyMatch(
                product -> product.id().equals("1")
                        && product.name().equals("AI 安全网关产品")));
        assertEquals("yong", buildOptions.users().get(0).account());
        assertEquals("127", systems.get(0).id());
        assertEquals("42", testTaskOptions.reports().get(0).id());
    }

    @Test
    void createsTestTaskFromTheSyncedBuildContext() {
        repository.connection = connection(List.of("1448"));
        service.sync(OWNER_ID, WORKSPACE_ID);

        var created = service.createTestTask(
                OWNER_ID,
                WORKSPACE_ID,
                repository.builds.get(0).id(),
                new ZentaoTestTaskCreateCommand(
                        List.of("system"),
                        "yong",
                        List.of("qa", "pm"),
                        "wait",
                        "2026-10-29",
                        "2026-11-05",
                        "42",
                        "V3.0.2_R100B002 系统测试"));

        assertEquals("901", created.remoteId());
        assertEquals("1448", gateway.createdTestTask.projectId());
        assertEquals("1448", gateway.createdTestTask.executionId());
        assertEquals("1", gateway.createdTestTask.productId());
        assertEquals("501", gateway.createdTestTask.buildId());
        assertEquals("qa,pm", gateway.createdTestTask.members());
    }

    @Test
    void commentsPassedCodexTestResultOnTheBug() {
        repository.connection = connection(List.of("1448"));
        repository.bugs.add(bug(false));

        boolean existed = service.commentTestResult(
                OWNER_ID,
                WORKSPACE_ID,
                1L,
                "run-20261029-001",
                "研发环境 · Chrome 141",
                "打开风险告警页，上下条切换正常。",
                List.of(new ZentaoTestAttachment(
                        "bug-fixed.png",
                        "image/png",
                        new byte[] {1, 2, 3})));

        assertFalse(existed);
        assertEquals("1024", gateway.commentedBugId);
        assertTrue(gateway.commentedText.contains("测试结论：通过"));
        assertTrue(gateway.commentedText.contains("研发环境 · Chrome 141"));
        assertTrue(gateway.commentedText.contains(
                "bug-fixed.png：http://zentao.example.com/file-read-1.png"));
        assertTrue(gateway.commentedText.contains(
                "<img src=\"http://zentao.example.com/file-read-1.png\""));
        assertEquals(1, gateway.uploadedFiles.size());
        assertEquals("bug-fixed.png", gateway.uploadedFiles.get(0).filename());
        assertEquals(
                "[WorkCove-Codex-Test:run-20261029-001]",
                gateway.commentMarker);
    }

    @Test
    void skipsScreenshotUploadsWhenTheTestCommentAlreadyExists() {
        repository.connection = connection(List.of("1448"));
        repository.bugs.add(bug(false));
        gateway.commentExists = true;

        boolean existed = service.commentTestResult(
                OWNER_ID,
                WORKSPACE_ID,
                1L,
                "run-20261029-001",
                "研发环境 · Chrome 141",
                "打开风险告警页，上下条切换正常。",
                List.of(new ZentaoTestAttachment(
                        "bug-fixed.png",
                        "image/png",
                        new byte[] {1, 2, 3})));

        assertTrue(existed);
        assertTrue(gateway.uploadedFiles.isEmpty());
        assertEquals("", gateway.commentedText);
    }

    private static ZentaoConnection connection(List<String> projectIds) {
        return new ZentaoConnection(
                1L,
                OWNER_ID,
                WORKSPACE_ID,
                "http://zentao.example.com/zentao",
                "yong",
                projectIds,
                "encrypted:secret",
                null,
                NOW,
                NOW);
    }

    private static ZentaoBug bug(boolean localDeleted) {
        return new ZentaoBug(
                1L,
                OWNER_ID,
                WORKSPACE_ID,
                "1024",
                "1448",
                "AI 安全网关",
                "1",
                "AI 安全网关产品",
                "同步白屏",
                "2",
                "1",
                "active",
                false,
                "刷新后白屏",
                "qa",
                "2026-10-27 09:00:00",
                "yong",
                false,
                localDeleted,
                false,
                "synced",
                "",
                null,
                null,
                null,
                NOW,
                NOW,
                NOW);
    }

    private static final class PassThroughCipher
            implements CredentialCipher {

        @Override
        public String encrypt(String rawValue) {
            return "encrypted:" + rawValue;
        }

        @Override
        public String decrypt(String encryptedValue) {
            return encryptedValue.replace("encrypted:", "");
        }
    }

    private static final class FakeGateway implements ZentaoGateway {

        private final List<String> loadedProjectIds = new ArrayList<>();
        private String mediaSource = "";
        private String confirmedBugId = "";
        private String confirmedAssignedTo = "";
        private String commentedBugId = "";
        private String commentedText = "";
        private String commentMarker = "";
        private boolean commentExists;
        private final List<UploadFile> uploadedFiles = new ArrayList<>();
        private CreateBuild createdBuild;
        private CreateTestTask createdTestTask;

        @Override
        public String authenticate(
                String baseUrl,
                String username,
                String password) {
            return "token";
        }

        @Override
        public List<Project> listProjects(String baseUrl, String token) {
            return List.of(
                    new Project("1448", "AI 安全网关", "doing"),
                    new Project("9999", "已关闭项目", "closed"));
        }

        @Override
        public List<Product> listProducts(String baseUrl, String token) {
            return List.of(new Product("2", "其他产品"));
        }

        @Override
        public Product getProduct(
                String baseUrl,
                String token,
                String productId) {
            return new Product(productId, "AI 安全网关产品");
        }

        @Override
        public List<RemoteBug> listProjectBugs(
                String baseUrl,
                String token,
                String projectId,
                String assignedTo) {
            loadedProjectIds.add(projectId);
            return List.of(new RemoteBug(
                    "1024",
                    projectId,
                    "",
                    "1",
                    "同步白屏",
                    "2",
                    "1",
                    "active",
                    false,
                    "刷新后白屏",
                    "qa",
                    "2026-10-27 09:00:00",
                    "yong"));
        }

        @Override
        public List<RemoteBuild> listProjectBuilds(
                String baseUrl,
                String token,
                String projectId) {
            return List.of(new RemoteBuild(
                    "501",
                    projectId,
                    "1",
                    projectId,
                    "v3.0.2-R100",
                    "success",
                    "panyong",
                    "2026-10-29 09:00:00",
                    "研发构建",
                    "abc123"));
        }

        @Override
        public List<RemoteExecution> listProjectExecutions(
                String baseUrl,
                String token,
                String projectId) {
            return List.of(new RemoteExecution(
                    "1455",
                    projectId,
                    "1",
                    "【主线】AISG_V3.0.2_R100",
                    "doing"));
        }

        @Override
        public List<RemoteSystem> listProductSystems(
                String baseUrl,
                String token,
                String productId) {
            return List.of(new RemoteSystem(
                    "127",
                    productId,
                    "大模型应用防火墙"));
        }

        @Override
        public List<RemoteUser> listUsers(
                String baseUrl,
                String token) {
            return List.of(
                    new RemoteUser("yong", "潘勇"),
                    new RemoteUser("qa", "测试"));
        }

        @Override
        public List<RemoteTestReport> listProductTestReports(
                String baseUrl,
                String token,
                String productId) {
            return List.of(new RemoteTestReport("42", "回归报告"));
        }

        @Override
        public CreatedBuild createBuild(
                String baseUrl,
                String token,
                CreateBuild createBuild) {
            createdBuild = createBuild;
            return new CreatedBuild("900", createBuild.name());
        }

        @Override
        public CreatedTestTask createTestTask(
                String baseUrl,
                String token,
                CreateTestTask createTestTask) {
            createdTestTask = createTestTask;
            return new CreatedTestTask("901", createTestTask.name());
        }

        @Override
        public void resolveBug(
                String baseUrl,
                String token,
                String remoteBugId,
                String assignedTo,
                String resolution,
                String comment) {
        }

        @Override
        public void confirmBug(
                String baseUrl,
                String token,
                String remoteBugId,
                String assignedTo,
                String comment) {
            confirmedBugId = remoteBugId;
            confirmedAssignedTo = assignedTo;
        }

        @Override
        public boolean commentBug(
                String baseUrl,
                String token,
                String remoteBugId,
                String comment,
                String marker) {
            commentedBugId = remoteBugId;
            commentedText = comment;
            commentMarker = marker;
            return false;
        }

        @Override
        public boolean hasBugComment(
                String baseUrl,
                String token,
                String remoteBugId,
                String marker) {
            return commentExists;
        }

        @Override
        public List<UploadedBugFile> uploadBugFiles(
                String baseUrl,
                String token,
                String remoteBugId,
                List<UploadFile> files) {
            uploadedFiles.addAll(files);
            return files.stream()
                    .map(file -> new UploadedBugFile(
                            "1",
                            file.filename(),
                            "http://zentao.example.com/file-read-1.png"))
                    .toList();
        }

        @Override
        public MediaFile fetchMedia(
                String baseUrl,
                String token,
                String source) {
            mediaSource = source;
            return new MediaFile(
                    new byte[] {1, 2, 3},
                    "image/png");
        }
    }

    private static final class InMemoryRepository
            implements ZentaoRepository {

        private ZentaoConnection connection;
        private final List<ZentaoBug> bugs = new ArrayList<>();
        private final List<ZentaoBuild> builds = new ArrayList<>();
        private long nextId = 1L;
        private long nextBuildId = 1L;

        @Override
        public Optional<ZentaoConnection> findConnection(
                long ownerId,
                String workspaceId) {
            return Optional.ofNullable(connection)
                    .filter((value) ->
                            value.ownerId() == ownerId
                                    && value.workspaceId().equals(workspaceId));
        }

        @Override
        public ZentaoConnection saveConnection(ZentaoConnection value) {
            connection = value;
            return value;
        }

        @Override
        public List<ZentaoBug> findBugs(long ownerId, String workspaceId) {
            return bugs.stream()
                    .filter((bug) -> !bug.localDeleted())
                    .toList();
        }

        @Override
        public Optional<ZentaoBug> findBug(
                long ownerId,
                String workspaceId,
                long id) {
            return bugs.stream()
                    .filter((bug) ->
                            bug.ownerId() == ownerId
                                    && bug.workspaceId().equals(workspaceId)
                                    && bug.id() == id)
                    .findFirst();
        }

        @Override
        public Optional<ZentaoBug> findBugByRemote(
                long ownerId,
                String workspaceId,
                String productId,
                String remoteId) {
            return bugs.stream()
                    .filter((bug) ->
                            bug.ownerId() == ownerId
                                    && bug.workspaceId().equals(workspaceId)
                                    && bug.productId().equals(productId)
                            && bug.remoteId().equals(remoteId))
                    .findFirst();
        }

        @Override
        public ZentaoBug saveBug(ZentaoBug bug) {
            ZentaoBug saved = bug.id() == null
                    ? new ZentaoBug(
                            nextId++,
                            bug.ownerId(),
                            bug.workspaceId(),
                            bug.remoteId(),
                            bug.projectId(),
                            bug.projectName(),
                            bug.productId(),
                            bug.productName(),
                            bug.title(),
                            bug.severity(),
                            bug.priority(),
                            bug.status(),
                            bug.confirmed(),
                            bug.steps(),
                            bug.openedBy(),
                            bug.openedDate(),
                            bug.assignedTo(),
                            bug.active(),
                            bug.localDeleted(),
                            bug.localResolved(),
                            bug.localStatus(),
                            bug.resolution(),
                            bug.solutionText(),
                            bug.reasonText(),
                            bug.pushedAt(),
                            bug.lastSyncedAt(),
                            bug.createdAt(),
                            bug.updatedAt())
                    : bug;
            bugs.removeIf((item) -> item.id().equals(saved.id()));
            bugs.add(saved);
            return saved;
        }

        @Override
        public void markBugsInactive(long ownerId, String workspaceId) {
            for (int index = 0; index < bugs.size(); index++) {
                ZentaoBug bug = bugs.get(index);
                if (bug.localDeleted()) continue;
                bugs.set(index, new ZentaoBug(
                        bug.id(),
                        bug.ownerId(),
                        bug.workspaceId(),
                        bug.remoteId(),
                        bug.projectId(),
                        bug.projectName(),
                        bug.productId(),
                        bug.productName(),
                        bug.title(),
                        bug.severity(),
                        bug.priority(),
                        bug.status(),
                        bug.confirmed(),
                        bug.steps(),
                        bug.openedBy(),
                        bug.openedDate(),
                        bug.assignedTo(),
                        false,
                        false,
                        bug.localResolved(),
                        bug.localStatus(),
                        bug.resolution(),
                        bug.solutionText(),
                        bug.reasonText(),
                        bug.pushedAt(),
                        bug.lastSyncedAt(),
                        bug.createdAt(),
                        bug.updatedAt()));
            }
        }

        @Override
        public void deleteBugs(long ownerId, String workspaceId) {
            bugs.clear();
        }

        @Override
        public void deleteBugLocally(
                long ownerId,
                String workspaceId,
                long bugId) {
            for (int index = 0; index < bugs.size(); index++) {
                ZentaoBug bug = bugs.get(index);
                if (bug.id() != bugId) continue;
                bugs.set(index, new ZentaoBug(
                        bug.id(),
                        bug.ownerId(),
                        bug.workspaceId(),
                        bug.remoteId(),
                        bug.projectId(),
                        bug.projectName(),
                        bug.productId(),
                        bug.productName(),
                        bug.title(),
                        bug.severity(),
                        bug.priority(),
                        bug.status(),
                        bug.confirmed(),
                        bug.steps(),
                        bug.openedBy(),
                        bug.openedDate(),
                        bug.assignedTo(),
                        false,
                        true,
                        bug.localResolved(),
                        bug.localStatus(),
                        bug.resolution(),
                        bug.solutionText(),
                        bug.reasonText(),
                        bug.pushedAt(),
                        bug.lastSyncedAt(),
                        bug.createdAt(),
                        bug.updatedAt()));
            }
        }

        @Override
        public void markBugResolvedLocally(
                long ownerId,
                String workspaceId,
                long bugId,
                String resolution,
                String solution,
                String reason) {
            for (int index = 0; index < bugs.size(); index++) {
                ZentaoBug bug = bugs.get(index);
                if (bug.id() != bugId) continue;
                bugs.set(index, new ZentaoBug(
                        bug.id(),
                        bug.ownerId(),
                        bug.workspaceId(),
                        bug.remoteId(),
                        bug.projectId(),
                        bug.projectName(),
                        bug.productId(),
                        bug.productName(),
                        bug.title(),
                        bug.severity(),
                        bug.priority(),
                        bug.status(),
                        bug.confirmed(),
                        bug.steps(),
                        bug.openedBy(),
                        bug.openedDate(),
                        bug.assignedTo(),
                        false,
                        false,
                        true,
                        "resolved",
                        resolution,
                        solution,
                        reason,
                        bug.pushedAt(),
                        bug.lastSyncedAt(),
                        bug.createdAt(),
                        bug.updatedAt()));
            }
        }

        @Override
        public List<ZentaoBuild> findBuilds(
                long ownerId,
                String workspaceId) {
            return builds.stream()
                    .filter((build) ->
                            build.ownerId() == ownerId
                                    && build.workspaceId().equals(workspaceId))
                    .toList();
        }

        @Override
        public Optional<ZentaoBuild> findBuildByRemote(
                long ownerId,
                String workspaceId,
                String projectId,
                String remoteId) {
            return builds.stream()
                    .filter((build) ->
                            build.ownerId() == ownerId
                                    && build.workspaceId().equals(workspaceId)
                                    && build.projectId().equals(projectId)
                                    && build.remoteId().equals(remoteId))
                    .findFirst();
        }

        @Override
        public ZentaoBuild saveBuild(ZentaoBuild build) {
            ZentaoBuild saved = build.id() == null
                    ? new ZentaoBuild(
                            nextBuildId++,
                            build.ownerId(),
                            build.workspaceId(),
                            build.remoteId(),
                            build.projectId(),
                            build.projectName(),
                            build.productId(),
                            build.productName(),
                            build.executionId(),
                            build.name(),
                            build.status(),
                            build.builder(),
                            build.buildDate(),
                            build.description(),
                            build.scmRevision(),
                            build.lastSyncedAt(),
                            build.createdAt(),
                            build.updatedAt())
                    : build;
            builds.removeIf((item) -> item.id().equals(saved.id()));
            builds.add(saved);
            return saved;
        }

        @Override
        public void deleteBuilds(long ownerId, String workspaceId) {
            builds.clear();
        }
    }
}
