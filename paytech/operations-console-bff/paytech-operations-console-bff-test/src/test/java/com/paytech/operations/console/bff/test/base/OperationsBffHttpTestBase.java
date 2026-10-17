package com.paytech.operations.console.bff.test.base;

import com.paytech.operations.console.bff.OperationsConsoleBffApplication;
import com.paytech.operations.console.bff.test.configuration.OperationsBffTestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 真实 BFF 入站和 Platform 出站 HTTP 链的唯一 Spring 测试配置。 */
@ActiveProfiles("auto")
@SpringBootTest(classes = {OperationsConsoleBffApplication.class, OperationsBffTestConfiguration.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class OperationsBffHttpTestBase {
    @DynamicPropertySource
    static void platformAddress(DynamicPropertyRegistry registry) {
        registry.add("operations.platform-test-base-url", OperationsBffTestConfiguration.PLATFORM::baseUrl);
        registry.add("operations.risk-test-base-url", OperationsBffTestConfiguration.RISK::baseUrl);
        registry.add("operations.client-review-risk-test-base-url", OperationsBffTestConfiguration.CLIENT_REVIEW::baseUrl);
        registry.add("operations.cct-test-base-url", OperationsBffTestConfiguration.CLIENT_REVIEW::baseUrl);
        registry.add("operations.console-origin", () -> "https://operations-console.local.test");
    }
}
