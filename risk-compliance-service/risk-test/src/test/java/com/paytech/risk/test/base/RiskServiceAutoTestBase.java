package com.paytech.risk.test.base;

import com.paytech.risk.RiskApplication;
import com.paytech.risk.test.infrastructure.RiskDatabasePrecondition;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * 从随机 Loopback HTTP 端口进入 Risk 真实生产链的服务级自动测试基类。
 */
@ActiveProfiles("auto")
@ContextConfiguration(initializers = RiskDatabasePrecondition.class)
@SpringBootTest(classes = RiskApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class RiskServiceAutoTestBase {

    @LocalServerPort
    protected int port;
}
