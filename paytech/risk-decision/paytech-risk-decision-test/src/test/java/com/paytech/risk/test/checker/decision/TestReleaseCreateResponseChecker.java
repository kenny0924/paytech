package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.test.checker.decision.RuleSetHistoryResponseChecker.time;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.test.builder.decision.CandidateBuilder.CompositionFacts;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.checker.decision.ReleaseStoreChecker.Created;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 创建 TEST Candidate 的 HTTP 响应 Checker
 * <li>业务字段按创建合同核对：DRAFT/version=1、TEST 环境、请求组成坐标与可信操作者。</li>
 * <li>服务端生成的发布 ID、内容摘要、时间与审计 ID 先由 {@link ReleaseStoreChecker} 核对持久化事实，再与响应逐字段比对。</li>
 *
 * @author Kenny
 */
public final class TestReleaseCreateResponseChecker extends AbstractHttpResponseChecker<TestReleaseCreateResponseChecker> {

    public TestReleaseCreateResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper,
        String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected TestReleaseCreateResponseChecker self() {
        return this;
    }

    /**
     * 成功创建：响应与持久化的根和初始事件一致。
     *
     * @return 新 Candidate 的发布 ID
     */
    public long assertCreated(ReleaseStoreChecker store, CompositionFacts composition, int releaseNo, long actorId,
        Instant notBefore, Instant notAfter) {
        assertStatus(200);
        JsonNode body = body();
        String releaseId = body.path("releaseId").asString();
        assertTrue(releaseId.matches("[1-9][0-9]{0,18}"), "发布 ID 必须为正 BIGINT 文本：" + releaseId);
        Created created = store.assertCreated(Long.parseLong(releaseId), composition, releaseNo, actorId, notBefore,
            notAfter);
        JsonNode initial = body.path("initialStateChange");
        assertAll("新 Candidate " + releaseId,
            () -> assertEquals(releaseNo, body.path("releaseNo").asInt()),
            () -> assertEquals(Long.toString(RuleSetBuilder.DECISION_POINT_ID), body.path("decisionPointId").asString()),
            () -> assertEquals(RuleSetBuilder.POINT_CODE, body.path("pointCode").asString()),
            () -> assertEquals(Long.toString(composition.pointVersionId()), body.path("pointVersionId").asString()),
            () -> assertEquals(Long.toString(composition.ruleSetVersionId()), body.path("ruleSetVersionId").asString()),
            () -> assertEquals(DecisionEnvironment.TEST.getCode(), body.path("environmentCode").asString()),
            () -> assertEquals(DecisionReleaseStatus.DRAFT.getCode(), body.path("status").asString()),
            () -> assertEquals(1, body.path("version").asInt()),
            () -> assertEquals(created.release().contentHash(), body.path("contentHash").asString()),
            () -> assertEquals(Long.toString(actorId), body.path("createdBy").asString()),
            () -> assertEquals(time(created.release().createTime()), body.path("createTime").asString()),
            () -> assertEquals(13, body.size(), "创建响应字段数量"),
            () -> assertEquals(Long.toString(created.initial().auditEventId()), initial.path("auditEventId").asString()),
            () -> assertEquals(time(created.release().createTime()), initial.path("eventTime").asString()),
            () -> assertTrue(initial.path("fromStatus").isNull(), "初始事件没有原状态"),
            () -> assertEquals(DecisionReleaseStatus.DRAFT.getCode(), initial.path("toStatus").asString()),
            () -> assertEquals("CANDIDATE_CREATED", initial.path("reasonCode").asString()),
            () -> assertEquals("OPERATIONS_COMMAND", initial.path("triggerType").asString()),
            () -> assertEquals(Long.toString(actorId), initial.path("actorId").asString()),
            () -> assertEquals(1, initial.path("aggregateVersion").asInt()),
            () -> assertEquals(8, initial.size(), "初始事件字段数量，不返回触发标识"));
        return Long.parseLong(releaseId);
    }

    /** 响应中的内容摘要，供多次创建互相比较。 */
    public String contentHash() {
        return body().path("contentHash").asString();
    }
}
