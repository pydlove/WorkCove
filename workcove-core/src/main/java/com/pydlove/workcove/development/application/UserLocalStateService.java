package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.domain.model.UserLocalState;
import com.pydlove.workcove.development.domain.repository.UserLocalStateRepository;
import com.pydlove.workcove.shared.domain.DomainException;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class UserLocalStateService {

    private static final Pattern STATE_KEY =
            Pattern.compile("[a-z0-9._-]{1,128}");
    private static final int MAX_PAYLOAD_LENGTH = 10 * 1024 * 1024;

    private final UserLocalStateRepository repository;
    private final Clock clock;

    public UserLocalStateService(
            UserLocalStateRepository repository,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    public Map<String, String> all(long ownerId) {
        Map<String, String> result = new LinkedHashMap<>();
        repository.findByOwnerId(ownerId).forEach(state ->
                result.put(state.stateKey(), state.payload()));
        return result;
    }

    public void save(long ownerId, String stateKey, String payload) {
        if (stateKey == null || !STATE_KEY.matcher(stateKey).matches()) {
            throw new DomainException("本地状态键无效");
        }
        if (payload == null || payload.isBlank()) {
            throw new DomainException("本地状态内容不能为空");
        }
        if (payload.length() > MAX_PAYLOAD_LENGTH) {
            throw new DomainException("本地状态内容超过 10MB");
        }
        repository.save(new UserLocalState(
                null,
                ownerId,
                stateKey,
                payload,
                clock.instant()));
    }
}
