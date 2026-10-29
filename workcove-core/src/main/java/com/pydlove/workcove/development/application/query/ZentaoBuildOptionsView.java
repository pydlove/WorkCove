package com.pydlove.workcove.development.application.query;

import com.pydlove.workcove.development.domain.port.ZentaoGateway;

import java.util.List;

public record ZentaoBuildOptionsView(
        List<Execution> executions,
        List<Product> products,
        List<User> users
) {

    public record Execution(
            String id,
            String projectId,
            String projectName,
            String productId,
            String name,
            String status
    ) {
    }

    public record Product(
            String id,
            String name
    ) {
    }

    public record User(
            String account,
            String name
    ) {
    }

    public static Execution execution(
            ZentaoGateway.RemoteExecution execution,
            String projectName) {
        return new Execution(
                execution.id(),
                execution.projectId(),
                projectName,
                execution.productId(),
                execution.name(),
                execution.status());
    }

    public static Product product(ZentaoGateway.Product product) {
        return new Product(product.id(), product.name());
    }

    public static User user(ZentaoGateway.RemoteUser user) {
        return new User(user.account(), user.name());
    }
}
