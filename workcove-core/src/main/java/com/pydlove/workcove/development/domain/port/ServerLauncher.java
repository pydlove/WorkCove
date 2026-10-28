package com.pydlove.workcove.development.domain.port;

import com.pydlove.workcove.development.domain.model.ServerTool;

public interface ServerLauncher {

    LaunchResult open(ServerConnection connection, ServerTool tool);

    record ServerConnection(
            String host,
            int port,
            String username,
            String password) {
    }

    record LaunchResult(boolean passwordCopied) {
    }
}
