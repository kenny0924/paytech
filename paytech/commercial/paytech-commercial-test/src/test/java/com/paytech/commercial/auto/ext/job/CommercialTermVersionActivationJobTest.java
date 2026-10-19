package com.paytech.commercial.auto.ext.job;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.ext.job.CommercialTermVersionActivationJobHandler;
import com.paytech.commercial.service.handler.CommercialTermVersionActivatedHandler;
import com.paytech.commercial.service.processor.response.CommercialTermActivationRunResponse;
import com.paytech.commercial.test.base.term.AbstractCommercialTermApiTest;
import com.paytech.commercial.test.builder.CommercialTermPlanBuilder;

import tools.jackson.databind.JsonNode;

/**
 * 定时激活作业入口：显式触发生产 XXL-Job Handler（测试中执行器关闭），经同一激活用例切换版本
 * <li>已生效第 1 版 + 已排期到期的第 2 版：作业激活第 2 版，第 1 版 SUPERSEDED 且终点等于第 2 版起点，系统命令快照操作人为 0，提交后发布事件。</li>
 * <li>没有到期版本时不做事；激活失败的版本保持 SCHEDULED 并计入失败数。</li>
 *
 * @see CommercialTermVersionActivationJobHandler#runOnce()
 */
class CommercialTermVersionActivationJobTest extends AbstractCommercialTermApiTest {
    @Autowired
    private CommercialTermVersionActivationJobHandler job;

    private CommercialTermVersion first;

    /** 每例：标准前置数据与已生效的第 1 版（一条通用卡 USD 价格）。 */
    @BeforeEach
    void setUpClassMocks() {
        mockBaseline();
        first = mockActiveVersion();
        mockPrice(first, 0, "USD", CARD, 30, "0.03");
    }

    /** 到期：第 2 版 ACTIVE、第 1 版 SUPERSEDED，事件携带被替代版本，审计为系统命令。 */
    @Test
    void test_dueVersionActivated() {
        JsonNode second = scheduledSecond();
        long secondId = Long.parseLong(text(second, "versionId"));

        CommercialTermActivationRunResponse result = job.runOnce();

        Map<String, Object> previous = testMapper.termVersion(first.getTermVersionId());
        List<Map<String, Object>> snapshots = testMapper.businessChangeSnapshots("COMMERCIAL_TERM_VERSION", secondId);
        Map<String, Object> activation = snapshots.getLast();
        assertAll(() -> assertEquals(1, result.getDueCount()), () -> assertEquals(1, result.getActivatedCount()),
            () -> assertEquals("ACTIVE", testMapper.termVersion(secondId).get("status")),
            () -> assertEquals("SUPERSEDED", previous.get("status")),
            () -> assertEquals(null, previous.get("active_slot")),
            () -> assertEquals(0L, ((Number)activation.get("create_by")).longValue()),
            () -> assertEquals("SYSTEM", json.readTree(activation.get("snapshot_json").toString()).path("_meta")
                .path("triggerType").asString()));
        long changeVersion = testMapper.businessChangeVersion("COMMERCIAL_TERM_PLAN", CommercialTermPlanBuilder.TERM_PLAN_ID);
        JsonNode payload = events.awaitEnvelope(CommercialTermVersionActivatedHandler.EVENT_TYPE,
            CommercialTermVersionActivatedHandler.buildEvent(CommercialTermPlanBuilder.TERM_PLAN_ID, changeVersion)
                .eventKey()).path("payload");
        assertAll(() -> assertEquals(Long.toString(secondId), payload.path("versionId").asString()),
            () -> assertEquals(Long.toString(first.getTermVersionId()), payload.path("supersededVersionId").asString()),
            () -> assertEquals(NOW.toString(), payload.path("effectiveFrom").asString()));
    }

    /** 没有到期版本：不做事。 */
    @Test
    void test_nothingDue() {
        CommercialTermActivationRunResponse result = job.runOnce();

        assertAll(() -> assertEquals(0, result.getDueCount()), () -> assertEquals(0, result.getFailedCount()),
            () -> assertEquals("ACTIVE", testMapper.termVersion(first.getTermVersionId()).get("status")));
    }

    /** 到期但激活校验失败（合同已终止）：计入失败，版本保持 SCHEDULED，旧版本不变。 */
    @Test
    void test_activationFailureKeepsScheduled() {
        JsonNode second = scheduledSecond();
        testMapper.terminateAgreement(first.getAgreementId());

        CommercialTermActivationRunResponse result = job.runOnce();

        assertAll(() -> assertEquals(1, result.getFailedCount()),
            () -> assertEquals("SCHEDULED", testMapper.termVersion(Long.parseLong(text(second, "versionId"))).get("status")),
            () -> assertEquals("ACTIVE", testMapper.termVersion(first.getTermVersionId()).get("status")));
    }

    /** 经 API 复制第 1 版创建第 2 版，审批后排期在 NOW（已到期）。 */
    JsonNode scheduledSecond() {
        JsonNode second = ok(201, createVersionResult(Long.toString(CommercialTermPlanBuilder.TERM_PLAN_ID), "2",
            Long.toString(first.getTermVersionId())));
        JsonNode scheduled = ok(200, scheduleResult(approved(second), NOW));
        assertEquals(Instant.parse(text(scheduled, "effectiveFrom")), NOW);
        return scheduled;
    }
}
