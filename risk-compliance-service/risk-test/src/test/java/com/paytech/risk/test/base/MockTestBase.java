package com.paytech.risk.test.base;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.TestBase;
import com.paytech.risk.RiskApplication;
import com.paytech.risk.test.checker.evaluation.SettlementRiskAssessmentChecker;
import com.paytech.risk.test.clear.RiskRedisCleaner;
import com.paytech.risk.test.infrastructure.RiskDatabasePrecondition;
import com.paytech.risk.test.mapper.compliance.MaterialProgramTestMapper;
import com.paytech.risk.test.mapper.decision.RuleSetTestMapper;
import com.paytech.risk.test.mapper.evaluation.SettlementRiskAssessmentTestMapper;
import com.paytech.risk.test.mocker.compliance.MaterialProgramMocker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;

import tools.jackson.databind.ObjectMapper;

/**
 * Risk 服务级入口测试的新规范基类
 * <li>复用正式启动类 RiskApplication 与生产组件扫描，只附加 mock Profile 配置；本地 MySQL/Redis 未就绪时在上下文启动前明确失败。</li>
 * <li>全局清理由 Foundation TestBase 在 AfterEach 统一执行所有 Clear 组件；后续接入本地数据 Mocker、外部 DTO Mocker 时在此增加两级初始化与意外调用检查。</li>
 */
@ActiveProfiles("mock")
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = RiskDatabasePrecondition.class)
@SpringBootTest(classes = RiskApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({RiskRedisCleaner.class, SettlementRiskAssessmentTestMapper.class, SettlementRiskAssessmentChecker.class,
    MaterialProgramTestMapper.class, MaterialProgramMocker.class, RuleSetTestMapper.class, RuleSetMocker.class})
public abstract class MockTestBase extends TestBase {
    @Autowired
    protected HttpTestInvoker http;

    @Autowired
    protected ObjectMapper objectMapper;
}
