package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.domain.model.UserLocalState;
import com.pydlove.workcove.development.domain.repository.UserLocalStateRepository;
import com.pydlove.workcove.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserLocalStateServiceTest {

    private static final long OWNER_ID = 10L;
    private static final Instant NOW =
            Instant.parse("2026-10-27T00:00:00Z");

    private final InMemoryRepository repository =
            new InMemoryRepository();
    private final UserLocalStateService service =
            new UserLocalStateService(
                    repository,
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void listsOnlyTheCurrentUsersState() {
        service.save(OWNER_ID, "workcove.profiles.v1", "{\"name\":\"A\"}");
        service.save(20L, "workcove.profiles.v1", "{\"name\":\"B\"}");

        assertEquals(
                "{\"name\":\"A\"}",
                service.all(OWNER_ID).get("workcove.profiles.v1"));
        assertEquals(
                "{\"name\":\"B\"}",
                service.all(20L).get("workcove.profiles.v1"));
    }

    @Test
    void rejectsInvalidStateKeys() {
        assertThrows(
                DomainException.class,
                () -> service.save(OWNER_ID, "../profile", "{}"));
    }

    @Test
    void rejectsEmptyPayloads() {
        assertThrows(
                DomainException.class,
                () -> service.save(OWNER_ID, "workcove.workspaces.v1", " "));
    }

    private static final class InMemoryRepository
            implements UserLocalStateRepository {

        private final List<UserLocalState> states = new ArrayList<>();
        private long nextId = 1L;

        @Override
        public List<UserLocalState> findByOwnerId(long ownerId) {
            return states.stream()
                    .filter((state) -> state.ownerId() == ownerId)
                    .toList();
        }

        @Override
        public UserLocalState save(UserLocalState state) {
            UserLocalState saved = new UserLocalState(
                    nextId++,
                    state.ownerId(),
                    state.stateKey(),
                    state.payload(),
                    state.updatedAt());
            states.removeIf((item) ->
                    item.ownerId() == saved.ownerId()
                            && item.stateKey().equals(saved.stateKey()));
            states.add(saved);
            return saved;
        }
    }
}
