package com.paytech.risk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * risk-decision 唯一启动入口（risk-decision-service）；扫描 com.paytech.risk 下的决策、名单与本 app 组件。
 */
@SpringBootApplication
public class RiskDecisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskDecisionApplication.class, args);
    }
}
