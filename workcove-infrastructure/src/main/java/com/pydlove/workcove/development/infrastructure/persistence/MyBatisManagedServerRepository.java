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
    public List<ManagedServer> findByOwnerIdAndWorkspaceId(
            long ownerId,
            String workspaceId) {
        return mapper.findByOwnerIdAndWorkspaceId(
                        ownerId,
                        workspaceId)
                .stream()
                .map(ManagedServerDataMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<ManagedServer> findByIdAndOwnerIdAndWorkspaceId(
            long id,
            long ownerId,
            String workspaceId) {
        return Optional.ofNullable(mapper.findByIdAndOwnerIdAndWorkspaceId(
                        id,
                        ownerId,
                        workspaceId))
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
    public void deleteByIdAndOwnerIdAndWorkspaceId(
            long id,
            long ownerId,
            String workspaceId) {
        mapper.deleteByIdAndOwnerIdAndWorkspaceId(
                id,
                ownerId,
                workspaceId);
    }
}
