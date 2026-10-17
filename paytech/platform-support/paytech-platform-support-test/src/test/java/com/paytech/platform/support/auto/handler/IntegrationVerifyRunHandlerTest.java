package com.paytech.platform.support.auto.handler;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.platform.support.api.enums.VerifyRunStatus;
import com.paytech.platform.support.api.externalapi.request.CreateVerifyAccountRequestBody;
import com.paytech.platform.support.api.externalapi.request.CreateVerifyCaseRequestBody;
import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationVerifyRun;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationVerifyCaseRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationVerifyRunRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationVerifyRunStepRepository;
import com.paytech.platform.support.integration.ext.eventhandler.IntegrationVerifyRunHandler;
import com.paytech.platform.support.test.base.ExternalApiTestBase;
import com.paytech.platform.support.test.builder.ExternalApiFixtures;
import com.paytech.platform.support.test.builder.ExternalApiSamples;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.mock.external.ChannelMockController;

/**
 * 沙箱验证运行的补偿任务入口：第一次执行真正调用渠道并写入结论；第二次执行（前一次中断）只记 INTERRUPTED、
 * 渠道零请求；已有结论的运行不再执行；不合法的事件键为终态失败。运行行由真实 Repository 插入（不经 V12，
 * 避免提交后唤醒的执行器与本例并发处理同一运行）。
 *
 * @see IntegrationVerifyRunHandler#handle(CompensationEvent)
 */
class IntegrationVerifyRunHandlerTest extends ExternalApiTestBase {

    private static final String ATTEMPT = "301000000000000001";

    @Autowired
    private IntegrationVerifyRunHandler handler;

    @Autowired
    private IntegrationVerifyRunRepository runs;

    @Autowired
    private IntegrationVerifyRunStepRepository steps;

    @Autowired
    private IntegrationVerifyCaseRepository cases;

    private Integration integration;

    /** 本类公共：沙箱接入方实现 PAY，验证账号 MAIN 与单步用例 PAY_ONCE。 */
    @BeforeEach
    void setUpClassMocks() {
        Operator admin = administrator();
        integration = externalApi.integration("SANDBOX", mockHttpServer.baseUrl() + ChannelMockController.PREFIX + "/api",
            true);
        externalApi.integrationApi(integration, "CALL", ExternalApiSamples.createOrderDefinition());
        externalApi.implementation(integration, externalApi.standardApi("PAY"), ExternalApiFixtures.flow("call-create-order"));
        String base = BASE + "/integrations/SANDBOX";
        post(base + "/verify-accounts", admin, new CreateVerifyAccountRequestBody("MAIN", "主账号", "DEMO", ATTEMPT, ATTEMPT,
            Map.of("outletId", "outlet-1"), Map.of("apiKey", "k"), "原因")).assertOk();
        post(base + "/verify-cases", admin, new CreateVerifyCaseRequestBody("PAY_ONCE", "用例", null, "MAIN",
            ExternalApiSamples.object("""
                {"s":[{"id":"pay","standardApiCode":"PAY","input":{"paymentContext":{"attemptId":"%s","amount":"1.00",
                  "vaultToken":"T"}},"expect":{"executeStatus":"SUCCESS"}}]}""".formatted(ATTEMPT)).get("s"), "原因"))
            .assertOk();
    }

    /** 第一次执行：调用渠道并写入 PASSED 与步骤；再次投递同一事件不再执行。 */
    @Test
    void test_firstExecution() {
        IntegrationVerifyRun run = run();
        channel.respond("POST", "/api/outlets/outlet-1/orders", 201, "{\"reference\":\"r-1\",\"state\":\"CAPTURED\"}");

        assertEquals(CompensationHandlerResult.success(), handler.handle(event(run, 1)));
        assertAll(() -> assertEquals(VerifyRunStatus.PASSED, runs.byId(run.getVerifyRunId()).getRunStatus()),
            () -> assertEquals(1, steps.byRun(run.getVerifyRunId()).size()), () -> assertEquals(1, channel.requests().size()));

        assertEquals(CompensationHandlerResult.success(), handler.handle(event(run, 1)));
        assertEquals(1, channel.requests().size());
    }

    /** 第二次执行说明前一次中断：记 INTERRUPTED，不写步骤，渠道零请求。 */
    @Test
    void test_interruptedNotReplayed() {
        IntegrationVerifyRun run = run();

        assertEquals(CompensationHandlerResult.success(), handler.handle(event(run, 2)));
        IntegrationVerifyRun stored = runs.byId(run.getVerifyRunId());
        assertAll(() -> assertEquals(VerifyRunStatus.INTERRUPTED, stored.getRunStatus()),
            () -> assertNotNull(stored.getFinishTime()), () -> assertTrue(steps.byRun(run.getVerifyRunId()).isEmpty()),
            () -> assertTrue(channel.requests().isEmpty()));
    }

    /** 事件键不是正整数或带载荷：终态失败，不访问运行。 */
    @Test
    void test_invalidIdentity() {
        for (var event : new CompensationEvent[] {new CompensationEvent(1, "abc", "INTEGRATION_VERIFY_RUN", "-", null, 1),
            new CompensationEvent(1, "12", "INTEGRATION_VERIFY_RUN", "-", "{}", 1)}) {
            assertEquals(CompensationHandlerResult.Outcome.TERMINAL_FAILURE, handler.handle(event).outcome());
        }
        assertTrue(channel.requests().isEmpty());
    }

    /** 按 V12 相同的快照形状插入 RUNNING 运行（不登记补偿任务）。 */
    private IntegrationVerifyRun run() {
        var verifyCase = cases.byCode(integration.getIntegrationId(), "PAY_ONCE");
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String snapshot = """
            {"integrationCode":"SANDBOX","caseCode":"PAY_ONCE","caseName":"用例","caseVersion":1,"accountId":%d,
             "accountCode":"MAIN","accountVersion":1,"steps":%s}""".formatted(verifyCase.getVerifyAccountId(),
            verifyCase.getStepsJson());
        var run = new IntegrationVerifyRun(OperatorBuilder.nextId(), verifyCase.getVerifyCaseId(),
            integration.getIntegrationId(), VerifyRunStatus.RUNNING, snapshot, null, null, null, 1L, now, null, now, now);
        runs.insert(run);
        return run;
    }

    private static CompensationEvent event(IntegrationVerifyRun run, int executeCount) {
        return new CompensationEvent(1, Long.toString(run.getVerifyRunId()), "INTEGRATION_VERIFY_RUN", "-", null,
            executeCount);
    }
}
