package com.pydlove.workcove.development.application.query;

import com.pydlove.workcove.development.domain.port.ZentaoGateway;

import java.util.List;

public record ZentaoTestTaskOptionsView(
        List<ZentaoBuildOptionsView.User> users,
        List<TestReport> reports
) {

    public record TestReport(
            String id,
            String title
    ) {
    }

    public static TestReport report(
            ZentaoGateway.RemoteTestReport report) {
        return new TestReport(report.id(), report.title());
    }
}
