package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.domain.model.WorkFileMatch;
import com.pydlove.workcove.development.domain.port.WorkFileSearchGateway;
import com.pydlove.workcove.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkFileSearchServiceTest {

    private final FakeGateway gateway = new FakeGateway();
    private final WorkFileSearchService service =
            new WorkFileSearchService(gateway);

    @Test
    void normalizesRootsAndQueryBeforeSearching() {
        List<WorkFileMatch> matches = service.search(
                List.of("/tmp/../tmp/project/"),
                "  AI 安全产品线  ");

        assertEquals(List.of(), matches);
        assertEquals(List.of("/tmp/project"), gateway.roots);
        assertEquals("AI 安全产品线", gateway.query);
    }

    @Test
    void validatesSearchInputBeforeCallingGateway() {
        assertThrows(
                DomainException.class,
                () -> service.search(List.of("relative"), "file"));
        assertEquals(List.of(), service.search(List.of("/tmp"), " "));
        assertThrows(
                DomainException.class,
                () -> service.search(
                        List.of("/tmp"),
                        "x".repeat(121)));
        assertEquals(0, gateway.calls);
    }

    @Test
    void normalizesTheFileAndRootsBeforeOpening() {
        service.openFile(
                "/tmp/../tmp/project/report.pptx",
                List.of("/tmp/../tmp/project/"));

        assertEquals("/tmp/project/report.pptx", gateway.openedPath);
        assertEquals(List.of("/tmp/project"), gateway.roots);
    }

    private static final class FakeGateway
            implements WorkFileSearchGateway {

        private List<String> roots = List.of();
        private String query = "";
        private String openedPath = "";
        private int calls;

        @Override
        public List<WorkFileMatch> search(
                List<String> roots,
                String query) {
            this.roots = roots;
            this.query = query;
            calls += 1;
            return List.of();
        }

        @Override
        public void openFile(String path, List<String> roots) {
            this.openedPath = path;
            this.roots = roots;
            calls += 1;
        }
    }
}
