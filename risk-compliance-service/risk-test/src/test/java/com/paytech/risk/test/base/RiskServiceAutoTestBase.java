package com.paytech.risk.test.base;

import com.paytech.risk.RiskApplication;
import com.paytech.risk.test.decision.RiskDecisionTestMapper;
import com.paytech.risk.test.infrastructure.RiskDatabasePrecondition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
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

    protected RiskDecisionTestMapper decisionTestMapper;

    protected com.paytech.risk.test.decision.RiskDictionaryTestMapper dictionaryTestMapper;

    protected com.paytech.risk.test.decision.RiskFlowTestMapper flowTestMapper;

    @Autowired
    void configureDecisionTestMapper(JdbcTemplate jdbcTemplate) {
        decisionTestMapper = new RiskDecisionTestMapper(jdbcTemplate);
        flowTestMapper = new com.paytech.risk.test.decision.RiskFlowTestMapper(jdbcTemplate);
        dictionaryTestMapper = new com.paytech.risk.test.decision.RiskDictionaryTestMapper(jdbcTemplate);
    }
}
