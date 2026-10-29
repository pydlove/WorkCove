package com.pydlove.workcove.development.domain.port;

import com.pydlove.workcove.development.domain.model.CodexAttachment;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** 连接本机 Codex app-server，并向网页客户端转发流式事件。 */
public interface CodexGateway {

    boolean isAvailable();

    boolean isConnected();

    String executable();

    Runnable subscribe(Consumer<Map<String, Object>> listener);

    Map<String, Object> startThread(String cwd, String sandbox);

    Map<String, Object> resumeThread(
            String threadId,
            String cwd,
            String sandbox);

    Map<String, Object> startTurn(
            String threadId,
            String text,
            String cwd,
            List<CodexAttachment> attachments,
            String model,
            String reasoningEffort,
            List<String> writableRoots);

    Map<String, Object> steerTurn(
            String threadId,
            String expectedTurnId,
            String text,
            List<CodexAttachment> attachments);

    Map<String, Object> listModels();

    Map<String, Object> interruptTurn(String threadId, String turnId);

    Map<String, Object> setThreadName(String threadId, String name);

    void close();
}
