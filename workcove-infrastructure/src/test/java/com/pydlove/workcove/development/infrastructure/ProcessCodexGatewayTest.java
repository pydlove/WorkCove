package com.pydlove.workcove.development.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.workcove.development.domain.model.CodexAttachment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessCodexGatewayTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsBrowserMcpConfigurationFromTheDesktopDescriptor()
            throws Exception {
        Path descriptor = temporaryDirectory.resolve(
                "browser-control.json");
        Files.writeString(
                descriptor,
                """
                {
                  "url": "http://127.0.0.1:43210",
                  "token": "desktop-token",
                  "command": "/Applications/WorkCove.app/WorkCove",
                  "script": "/tmp/browser-mcp.js"
                }
                """);

        Map<String, String> environment =
                new ProcessCodexGateway(new ObjectMapper())
                        .browserMcpEnvironment(Map.of(
                                "WORKCOVE_BROWSER_CONTROL_FILE",
                                descriptor.toString()));

        assertEquals(
                "http://127.0.0.1:43210",
                environment.get("WORKCOVE_BROWSER_CONTROL_URL"));
        assertEquals(
                "desktop-token",
                environment.get("WORKCOVE_BROWSER_CONTROL_TOKEN"));
        assertEquals(
                "/Applications/WorkCove.app/WorkCove",
                environment.get("WORKCOVE_BROWSER_MCP_COMMAND"));
        assertEquals(
                "/tmp/browser-mcp.js",
                environment.get("WORKCOVE_BROWSER_MCP_SCRIPT"));
    }

    @Test
    void startsAppServerWithoutBrowserMcpWhenConfigurationIsMissing() {
        assertEquals(
                List.of("/usr/local/bin/codex", "app-server"),
                ProcessCodexGateway.appServerArguments(
                        "/usr/local/bin/codex",
                        Map.of()));
    }

    @Test
    void configuresWorkCoveBrowserMcpForAppServer() {
        List<String> arguments =
                ProcessCodexGateway.appServerArguments(
                        "/Applications/WorkCove.app/Contents/MacOS/WorkCove",
                        Map.of(
                                "WORKCOVE_BROWSER_MCP_COMMAND",
                                "/Applications/WorkCove.app/Contents/MacOS/WorkCove",
                                "WORKCOVE_BROWSER_MCP_SCRIPT",
                                "/tmp/WorkCove browser/browser-mcp.js",
                                "WORKCOVE_BROWSER_CONTROL_URL",
                                "http://127.0.0.1:43210",
                                "WORKCOVE_BROWSER_CONTROL_TOKEN",
                                "test-token"));

        assertEquals(
                "/Applications/WorkCove.app/Contents/MacOS/WorkCove",
                arguments.get(0));
        assertEquals("app-server", arguments.get(1));
        assertTrue(arguments.contains(
                "mcp_servers.workcove_browser.command="
                        + "\"/Applications/WorkCove.app/Contents/MacOS/WorkCove\""));
        assertTrue(arguments.contains(
                "mcp_servers.workcove_browser.args="
                        + "[\"/tmp/WorkCove browser/browser-mcp.js\"]"));
        assertTrue(arguments.contains(
                "mcp_servers.workcove_browser.env_vars="
                        + "[\"ELECTRON_RUN_AS_NODE\","
                        + "\"WORKCOVE_BROWSER_CONTROL_URL\","
                        + "\"WORKCOVE_BROWSER_CONTROL_TOKEN\"]"));
        assertFalse(arguments.stream().anyMatch(
                argument -> argument.contains("test-token")));
    }

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
                                "workspace-a",
                                "screen.png",
                                "image/png",
                                10,
                                Path.of("/tmp/screen.png")),
                        new CodexAttachment(
                                "file-1",
                                "workspace-a",
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

    @Test
    @SuppressWarnings("unchecked")
    void buildsSteerInputForTheExpectedActiveTurn() {
        ProcessCodexGateway gateway =
                new ProcessCodexGateway(new ObjectMapper());

        Map<String, Object> params = gateway.steerParams(
                "thread-1",
                "turn-1",
                "请优先检查登录后的页面",
                List.of(new CodexAttachment(
                        "image-1",
                        "workspace-a",
                        "screen.png",
                        "image/png",
                        10,
                        Path.of("/tmp/screen.png"))));

        assertEquals("thread-1", params.get("threadId"));
        assertEquals("turn-1", params.get("expectedTurnId"));
        List<Map<String, Object>> input =
                (List<Map<String, Object>>) params.get("input");
        assertEquals("text", input.get(0).get("type"));
        assertEquals(
                "请优先检查登录后的页面",
                input.get(0).get("text"));
        assertEquals("localImage", input.get(1).get("type"));
        assertFalse(params.containsKey("cwd"));
        assertFalse(params.containsKey("model"));
    }
}
