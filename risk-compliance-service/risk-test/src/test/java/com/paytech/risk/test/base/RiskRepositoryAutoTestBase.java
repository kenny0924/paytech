package com.paytech.risk.test.base;

import com.paytech.risk.RiskApplication;
import com.paytech.risk.test.decision.RiskDecisionTestMapper;
import com.paytech.risk.test.infrastructure.RiskDatabasePrecondition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * 使用仓库自有 Loopback MySQL 和真实 MyBatis 配置的 Repository 自动测试基类。
 */
@ActiveProfiles("auto")
@ContextConfiguration(initializers = RiskDatabasePrecondition.class)
@SpringBootTest(classes = RiskApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class RiskRepositoryAutoTestBase {

    protected RiskDecisionTestMapper decisionTestMapper;

    @Autowired
    void configureDecisionTestMapper(JdbcTemplate jdbcTemplate) {
        decisionTestMapper = new RiskDecisionTestMapper(jdbcTemplate);
    }
}
