package com.pydlove.workcove.development.infrastructure.persistence;

import com.pydlove.workcove.development.domain.model.ManagedServer;

final class ManagedServerDataMapper {

    private ManagedServerDataMapper() {
    }

    static ManagedServer toDomain(ManagedServerData data) {
        return ManagedServer.restore(
                data.getId(),
                data.getOwnerId(),
                data.getWorkspaceId(),
                data.getName(),
                data.getHost(),
                data.getPort(),
                data.getUsername(),
                data.getPasswordCiphertext(),
                data.getHostKeyAlgorithm(),
                data.getHostKeyFingerprint(),
                data.getHostKeyPublicKey(),
                data.getCreatedAt(),
                data.getUpdatedAt());
    }

    static ManagedServerData toData(ManagedServer server) {
        ManagedServerData data = new ManagedServerData();
        data.setId(server.getId());
        data.setOwnerId(server.getOwnerId());
        data.setWorkspaceId(server.getWorkspaceId());
        data.setName(server.getName());
        data.setHost(server.getHost());
        data.setPort(server.getPort());
        data.setUsername(server.getUsername());
        data.setPasswordCiphertext(server.getPasswordCiphertext());
        data.setHostKeyAlgorithm(server.getHostKeyAlgorithm());
        data.setHostKeyFingerprint(server.getHostKeyFingerprint());
        data.setHostKeyPublicKey(server.getHostKeyPublicKey());
        data.setCreatedAt(server.getCreatedAt());
        data.setUpdatedAt(server.getUpdatedAt());
        return data;
    }
}
