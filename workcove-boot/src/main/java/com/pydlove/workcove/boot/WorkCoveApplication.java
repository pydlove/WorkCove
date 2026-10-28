package com.pydlove.workcove.boot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.pydlove.workcove")
@EnableScheduling
@MapperScan({
        "com.pydlove.workcove.identity.infrastructure.persistence",
        "com.pydlove.workcove.development.infrastructure.persistence"
})
public class WorkCoveApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkCoveApplication.class, args);
    }
}
