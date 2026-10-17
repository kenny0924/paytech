package com.paytech.risk.test.base;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import com.paytech.foundation.compensation.core.execution.CompensationEventDispatcher;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.TestBase;
import com.paytech.risk.RiskDecisionApplication;
import com.paytech.risk.test.checker.decision.DictionaryRecordChecker;
import com.paytech.risk.test.checker.decision.FlowStoreChecker;
import com.paytech.risk.test.checker.decision.ReleaseStoreChecker;
import com.paytech.risk.test.checker.decision.RuleSetStoreChecker;
import com.paytech.risk.test.checker.decision.VariableDataChecker;
import com.paytech.risk.test.checker.evaluation.SettlementRiskAssessmentChecker;
import com.paytech.risk.test.clear.RiskDecisionDataCleaner;
import com.paytech.risk.test.clear.RiskRedisCleaner;
import com.paytech.risk.test.infrastructure.RiskDecisionDatabasePrecondition;
import com.paytech.risk.test.mapper.decision.DictionaryReferenceTestMapper;
import com.paytech.risk.test.mapper.decision.DictionaryTestMapper;
import com.paytech.risk.test.mapper.decision.FlowTestMapper;
import com.paytech.risk.test.mapper.decision.ReleaseTestMapper;
import com.paytech.risk.test.mapper.decision.RuleSetTestMapper;
import com.paytech.risk.test.mapper.decision.VariableTestMapper;
import com.paytech.risk.test.mapper.evaluation.CheckoutRiskTestMapper;
import com.paytech.risk.test.mapper.evaluation.PaymentRiskTestMapper;
import com.paytech.risk.test.mapper.evaluation.SettlementRiskAssessmentTestMapper;
import com.paytech.risk.test.mocker.decision.CandidateMocker;
import com.paytech.risk.test.mocker.decision.DictionaryMocker;
import com.paytech.risk.test.mocker.decision.DictionaryReferenceMocker;
import com.paytech.risk.test.mocker.decision.FlowMocker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;
import com.paytech.risk.test.mocker.decision.VariableMocker;
import com.paytech.risk.test.mocker.evaluation.CheckoutRiskMocker;
import com.paytech.risk.test.mocker.evaluation.PaymentRiskMocker;

import tools.jackson.databind.ObjectMapper;

/**
 * risk-decision 服务级入口测试基类（由原 Risk MockTestBase 拆出，design/10 §2.4）
 * <li>复用正式启动类 RiskDecisionApplication 与生产组件扫描，只附加 mock Profile 配置；本地 mor_risk_decision / Redis 未就绪时在上下文启动前明确失败。</li>
 * <li>只导入本服务的 Mocker / TestMapper / Checker（decision 与 evaluation），不再导入 compliance 组件。</li>
 * <li>全局 BeforeEach：前例异步已停止且 mor_risk_decision 为空基线（目录行完整）。全局 AfterEach：等待补偿任务（DECISION_SHADOW_RUN）
 * 处理停止后再交给 Foundation TestBase 执行全部 Clear。本服务没有出站 HTTP，无外部 DTO Mock 需要做意外调用检查。</li>
 */
@ActiveProfiles("mock")
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = RiskDecisionDatabasePrecondition.class)
@SpringBootTest(classes = RiskDecisionApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({RiskRedisCleaner.class, RiskDecisionDataCleaner.class, SettlementRiskAssessmentTestMapper.class,
    SettlementRiskAssessmentChecker.class, RuleSetTestMapper.class, RuleSetMocker.class, RuleSetStoreChecker.class,
    FlowTestMapper.class, FlowMocker.class, FlowStoreChecker.class, ReleaseTestMapper.class, CandidateMocker.class,
    ReleaseStoreChecker.class, DictionaryTestMapper.class, DictionaryReferenceTestMapper.class, DictionaryMocker.class,
    DictionaryReferenceMocker.class, DictionaryRecordChecker.class,
    VariableTestMapper.class, VariableMocker.class, VariableDataChecker.class,
    PaymentRiskTestMapper.class, PaymentRiskMocker.class, CheckoutRiskTestMapper.class, CheckoutRiskMocker.class})
public abstract class RiskDecisionMockTestBase extends TestBase {
    /** 补偿处理停止的最长等待。 */
    private static final long ASYNC_STOP_MILLIS = 10_000;

    private static boolean asynchronousWorkStopped = true;

    @Autowired
    protected HttpTestInvoker http;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private RiskDecisionDataCleaner dataCleaner;

    @Autowired
    private CompensationEventDispatcher compensationDispatcher;

    @Autowired
    @Qualifier("compensationHandlerExecutor")
    private ExecutorService compensationHandlers;

    /** 两级初始化的第一层：上一例的异步处理已结束，库为空基线。 */
    @BeforeEach
    protected final void setUpRiskDecisionBaseline() {
        Assertions.assertTrue(asynchronousWorkStopped, "前例异步处理未结束，禁止复用环境");
        dataCleaner.assertBaseline();
    }

    /** 等待补偿处理停止；超时则保留现场并拒绝清理与复用。 */
    @AfterEach
    protected final void stopRiskDecisionAsynchronousWork() {
        long deadline = System.currentTimeMillis() + ASYNC_STOP_MILLIS;
        while (!compensationIdle()) {
            if (System.currentTimeMillis() > deadline) {
                asynchronousWorkStopped = false;
                Assertions.fail("补偿任务在 " + ASYNC_STOP_MILLIS + " ms 内未停止");
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                asynchronousWorkStopped = false;
                Assertions.fail("等待补偿任务停止被中断");
            }
        }
    }

    /** 异步无法停止时保留现场，防止公共清理之后仍被后台处理回写。 */
    @Override
    protected void checkClearAllowed() {
        Assertions.assertTrue(asynchronousWorkStopped, "异步处理未结束，禁止清理环境");
    }

    private boolean compensationIdle() {
        boolean handlersIdle = !(compensationHandlers instanceof ThreadPoolExecutor pool) || pool.getActiveCount() == 0;
        return compensationDispatcher.activeTaskCount() == 0 && compensationDispatcher.queuedTaskCount() == 0
            && handlersIdle;
    }
}
