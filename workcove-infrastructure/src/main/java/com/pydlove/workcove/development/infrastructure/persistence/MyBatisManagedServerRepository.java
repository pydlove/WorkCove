package com.pydlove.workcove.development.infrastructure.persistence;

import com.pydlove.workcove.development.domain.model.ManagedServer;
import com.pydlove.workcove.development.domain.repository.ManagedServerRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisManagedServerRepository implements ManagedServerRepository {

    private final ManagedServerMapper mapper;

    public MyBatisManagedServerRepository(ManagedServerMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ManagedServer> findByOwnerId(long ownerId) {
        return mapper.findByOwnerId(ownerId).stream()
                .map(ManagedServerDataMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<ManagedServer> findByIdAndOwnerId(long id, long ownerId) {
        return Optional.ofNullable(mapper.findByIdAndOwnerId(id, ownerId))
                .map(ManagedServerDataMapper::toDomain);
    }

    @Override
    public ManagedServer save(ManagedServer server) {
        ManagedServerData data = ManagedServerDataMapper.toData(server);
        if (data.getId() == null) {
            mapper.insert(data);
        } else {
            mapper.update(data);
        }
        return ManagedServerDataMapper.toDomain(data);
    }

    @Override
    public void deleteByIdAndOwnerId(long id, long ownerId) {
        mapper.deleteByIdAndOwnerId(id, ownerId);
    }
}
