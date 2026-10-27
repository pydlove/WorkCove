package com.pydlove.workcove.boot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.pydlove.workcove")
@MapperScan("com.pydlove.workcove.identity.infrastructure.persistence")
public class WorkCoveApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkCoveApplication.class, args);
    }
}
