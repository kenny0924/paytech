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
import com.paytech.compliance.test.checker.QualificationStoreChecker;
import com.paytech.compliance.test.clear.ComplianceDataCleaner;
import com.paytech.compliance.test.configuration.ComplianceTestMockConfiguration;
import com.paytech.compliance.test.infrastructure.ComplianceDatabasePrecondition;
import com.paytech.compliance.test.mapper.ProgramTestMapper;
import com.paytech.compliance.test.mapper.QualificationTestMapper;
import com.paytech.compliance.test.mapper.ReviewTestMapper;
import com.paytech.compliance.test.mocker.LegacyCaseMocker;
import com.paytech.compliance.test.mocker.ProgramPackMocker;
import com.paytech.compliance.test.mocker.QualificationMocker;
import com.paytech.compliance.test.mocker.external.PartyProfileMocker;
import com.paytech.compliance.test.mocker.external.ScreeningMocker;
import com.paytech.compliance.test.probe.ComplianceEventProbe;
import com.paytech.foundation.testsupport.api.MockHttpServer;
import com.paytech.foundation.compensation.core.execution.CompensationEventDispatcher;
import com.paytech.foundation.messaging.ordering.AggregateKeySequencer;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.TestBase;

import tools.jackson.databind.ObjectMapper;

/**
 * compliance 服务级入口测试基类（由原 Risk MockTestBase 拆出，design/10 §2.4）
 * <li>复用正式启动类 ComplianceApplication 与生产组件扫描，只附加 mock Profile 配置；本地 mor_compliance 未就绪时在上下文启动前明确失败。</li>
 * <li>导入程序夹具、审核只读 Mapper、资格 Mocker / Checker、compliance.events 观察者与外部 Mock 服务配置（channel-connector、
 * client-master 在独立 Loopback 服务上应答）。</li>
 * <li>全局 BeforeEach：前例异步已停止、mor_compliance 与外部 Mock 为空基线，并绑定本例的 compliance.events 观察队列（使发布可路由）。</li>
 * <li>全局 AfterEach：等待补偿任务与 Mock 服务空闲、删除观察队列、检查外部意外调用，再交给 Foundation TestBase 执行全部 Clear。</li>
 */
@ActiveProfiles("mock")
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = ComplianceDatabasePrecondition.class)
@SpringBootTest(classes = ComplianceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ComplianceTestMockConfiguration.class, ComplianceDataCleaner.class, ProgramTestMapper.class, ProgramPackMocker.class,
    LegacyCaseMocker.class, ReviewTestMapper.class, ComplianceEventProbe.class, QualificationTestMapper.class, QualificationMocker.class,
    QualificationStoreChecker.class})
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
    protected ComplianceEventProbe events;

    @Autowired
    protected ScreeningMocker screening;

    @Autowired
    protected PartyProfileMocker partyProfiles;

    @Autowired
    protected MockHttpServer mockHttpServer;

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
        screening.assertEmpty();
        partyProfiles.assertEmpty();
        events.bind();
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
        mockHttpServer.awaitIdle();
        events.unbind();
        Assertions.assertAll("外部意外调用", screening::assertNoUnexpectedCalls, partyProfiles::assertNoUnexpectedCalls,
            mockHttpServer::assertNoUnexpectedRequests);
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
