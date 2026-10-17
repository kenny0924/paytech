package com.paytech.compliance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * compliance 唯一启动入口（compliance-service）；扫描 com.paytech.compliance 下的合规业务与本 app 组件。
 */
@SpringBootApplication
public class ComplianceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComplianceApplication.class, args);
    }
}
