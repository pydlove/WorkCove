package com.pydlove.workcove.development.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.workcove.development.domain.model.CodexAttachment;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProcessCodexGatewayTest {

    @Test
    void reportsAnAlreadyStoppedTaskWithoutStartingANewProcess() {
        ProcessCodexGateway gateway =
                new ProcessCodexGateway(new ObjectMapper());

        assertFalse(gateway.isConnected());
        Map<String, Object> result =
                gateway.interruptTurn("thread-1", "turn-1");

        assertEquals(true, result.get("alreadyStopped"));
        assertEquals(false, result.get("forced"));
        assertFalse(gateway.isConnected());
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildsTurnInputWithImagesFilesAndModelOverrides() {
        ProcessCodexGateway gateway =
                new ProcessCodexGateway(new ObjectMapper());

        Map<String, Object> params = gateway.turnParams(
                "thread-1",
                "Inspect these attachments",
                "/tmp/project",
                List.of(
                        new CodexAttachment(
                                "image-1",
                                "screen.png",
                                "image/png",
                                10,
                                Path.of("/tmp/screen.png")),
                        new CodexAttachment(
                                "file-1",
                                "notes.txt",
                                "text/plain",
                                5,
                                Path.of("/tmp/notes.txt"))),
                "deepseek-flash-official",
                "xhigh",
                List.of("/tmp/test-evidence"));

        List<Map<String, Object>> input =
                (List<Map<String, Object>>) params.get("input");
        assertEquals("text", input.get(0).get("type"));
        assertEquals("localImage", input.get(1).get("type"));
        assertEquals("/tmp/screen.png", input.get(1).get("path"));
        assertEquals("mention", input.get(2).get("type"));
        assertEquals("notes.txt", input.get(2).get("name"));
        assertEquals("deepseek-flash-official", params.get("model"));
        assertEquals("xhigh", params.get("effort"));
        Map<String, Object> sandboxPolicy =
                (Map<String, Object>) params.get("sandboxPolicy");
        assertEquals("workspaceWrite", sandboxPolicy.get("type"));
        assertEquals(
                List.of("/tmp/project", "/tmp/test-evidence"),
                sandboxPolicy.get("writableRoots"));
    }
}
