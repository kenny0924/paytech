package com.paytech.compliance.test.base;

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

import com.paytech.compliance.ComplianceApplication;
import com.paytech.compliance.test.checker.ClientReviewStoreChecker;
import com.paytech.compliance.test.checker.QualificationStoreChecker;
import com.paytech.compliance.test.clear.ComplianceDataCleaner;
import com.paytech.compliance.test.infrastructure.ComplianceDatabasePrecondition;
import com.paytech.compliance.test.mapper.ClientReviewTestMapper;
import com.paytech.compliance.test.mapper.MaterialProgramTestMapper;
import com.paytech.compliance.test.mapper.QualificationTestMapper;
import com.paytech.compliance.test.mocker.ClientReviewMocker;
import com.paytech.compliance.test.mocker.MaterialProgramMocker;
import com.paytech.compliance.test.mocker.QualificationMocker;
import com.paytech.compliance.test.probe.ClientReviewResultProbe;
import com.paytech.foundation.compensation.core.execution.CompensationEventDispatcher;
import com.paytech.foundation.messaging.ordering.AggregateKeySequencer;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.TestBase;

import tools.jackson.databind.ObjectMapper;

/**
 * compliance 服务级入口测试基类（由原 Risk MockTestBase 拆出，design/10 §2.4）
 * <li>复用正式启动类 ComplianceApplication 与生产组件扫描，只附加 mock Profile 配置；本地 mor_compliance 未就绪时在上下文启动前明确失败。</li>
 * <li>只导入 compliance 的 Mocker / TestMapper / Checker（入驻审核与角色资格）与 ClientReviewResultProbe。</li>
 * <li>全局 BeforeEach：前例异步已停止且 mor_compliance 为空基线。全局 AfterEach：等待补偿任务（CLIENT_REVIEW_RESULT 双写发布）处理停止后
 * 再交给 Foundation TestBase 执行全部 Clear。本服务没有出站 HTTP，无外部 DTO Mock 需要做意外调用检查。</li>
 */
@ActiveProfiles("mock")
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = ComplianceDatabasePrecondition.class)
@SpringBootTest(classes = ComplianceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ComplianceDataCleaner.class, MaterialProgramTestMapper.class, MaterialProgramMocker.class,
    ClientReviewTestMapper.class, ClientReviewMocker.class, ClientReviewStoreChecker.class, ClientReviewResultProbe.class,
    QualificationTestMapper.class, QualificationMocker.class, QualificationStoreChecker.class})
public abstract class ComplianceMockTestBase extends TestBase {
    /** 补偿处理停止的最长等待。 */
    private static final long ASYNC_STOP_MILLIS = 10_000;

    private static boolean asynchronousWorkStopped = true;

    @Autowired
    private AggregateKeySequencer sequencer;

    @Autowired
    protected HttpTestInvoker http;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private ComplianceDataCleaner dataCleaner;

    @Autowired
    private CompensationEventDispatcher compensationDispatcher;

    @Autowired
    @Qualifier("compensationHandlerExecutor")
    private ExecutorService compensationHandlers;

    /** 两级初始化的第一层：上一例的异步处理已结束，库为空基线。 */
    @BeforeEach
    protected final void setUpComplianceBaseline() {
        Assertions.assertTrue(asynchronousWorkStopped, "前例异步处理未结束，禁止复用环境");
        dataCleaner.assertBaseline();
    }

    /** 等待补偿处理停止；超时则保留现场并拒绝清理与复用。 */
    @AfterEach
    protected final void stopComplianceAsynchronousWork() {
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

    /** 异步无法停止时保留现场，防止公共清理之后仍被后台发布回写。 */
    @Override
    protected void checkClearAllowed() {
        Assertions.assertTrue(asynchronousWorkStopped, "异步处理未结束，禁止清理环境");
    }

    private boolean compensationIdle() {
        boolean handlersIdle = !(compensationHandlers instanceof ThreadPoolExecutor pool) || pool.getActiveCount() == 0;
        // 按聚合串行时已 Claim 任务在编排线程上排队，协调器空闲不代表发布已结束。
        return compensationDispatcher.activeTaskCount() == 0 && compensationDispatcher.queuedTaskCount() == 0
            && sequencer.pendingTaskCount() == 0 && handlersIdle;
    }
}
