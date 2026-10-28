package com.pydlove.workcove.development.interfaces.dto;

import com.pydlove.workcove.development.domain.model.ServerTool;

public record OpenManagedServerResponse(
        ServerTool tool,
        boolean passwordCopied) {
}
