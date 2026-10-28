package com.pydlove.workcove.development.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MacApplicationHostTest {

    @Test
    void detectsRunningApplicationsFromOneProcessSnapshot() {
        String chrome = "/Applications/Google Chrome.app";
        String idea = "/Applications/IntelliJ IDEA.app";
        String processes = """
                  101 /Applications/Google Chrome.app/Contents/MacOS/Google Chrome
                  202 /Applications/IntelliJ IDEA.app/Contents/MacOS/idea --wait
                  303 /Applications/Other.app/Contents/MacOS/Other
                """;

        Set<String> running = MacApplicationHost.matchingApplicationPaths(
                List.of(chrome, idea, "/Applications/Closed.app"),
                processes);

        assertEquals(Set.of(chrome, idea), running);
    }

    @Test
    void ignoresInvalidPathsAndAlreadyClosedApplications() {
        String processes = """
                  101 /Applications/Google Chrome.app/Contents/MacOS/Google Chrome
                """;

        Set<String> running = MacApplicationHost.matchingApplicationPaths(
                List.of("", "/tmp/not-an-app", "/Applications/Closed.app"),
                processes);

        assertEquals(Set.of(), running);
    }
}
