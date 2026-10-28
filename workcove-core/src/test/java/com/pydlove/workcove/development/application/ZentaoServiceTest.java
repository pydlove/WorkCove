package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.application.query.ZentaoSyncResult;
import com.pydlove.workcove.development.domain.model.ZentaoBug;
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
        assertEquals(List.of("1448"), gateway.loadedProjectIds);
        assertEquals("1448", repository.bugs.get(0).projectId());
        assertEquals("AI 安全网关", repository.bugs.get(0).projectName());
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
            return List.of(new Product("1", "AI 安全网关产品"));
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
                    "刷新后白屏",
                    "qa",
                    "2026-10-27 09:00:00",
                    "yong"));
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
        private long nextId = 1L;

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
    }
}
