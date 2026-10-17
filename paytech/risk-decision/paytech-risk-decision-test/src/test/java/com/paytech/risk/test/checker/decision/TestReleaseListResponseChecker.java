package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.test.checker.decision.RuleSetHistoryResponseChecker.time;
import static com.paytech.risk.test.checker.decision.TestReleaseDetailResponseChecker.assertBoolean;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.mocker.decision.CandidateMocker.MockedCandidate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TEST Candidate 分页列表的 HTTP 响应 Checker
 * <li>分页元数据与条目顺序分开断言；条目逐字段核对插入的 Candidate。</li>
 * <li>三态检查结果按夹具事实：无检查为 null，八项全通过为 true，否则为 false。</li>
 *
 * @author Kenny
 */
public final class TestReleaseListResponseChecker extends AbstractHttpResponseChecker<TestReleaseListResponseChecker> {

    public TestReleaseListResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper,
        String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected TestReleaseListResponseChecker self() {
        return this;
    }

    /** 成功响应及分页元数据。 */
    public TestReleaseListResponseChecker assertPage(int page, int size, long total) {
        assertStatus(200);
        JsonNode body = body();
        assertAll(() -> assertEquals(page, body.path("page").asInt(), "page"),
            () -> assertEquals(size, body.path("size").asInt(), "size"),
            () -> assertEquals(total, body.path("total").asLong(), "total"),
            () -> assertEquals(4, body.size(), "分页字段数量"));
        return this;
    }

    /** 当前页按给定顺序返回这些 Candidate，逐项核对全部字段。 */
    public TestReleaseListResponseChecker assertItems(MockedCandidate... expected) {
        JsonNode items = body().path("items");
        assertEquals(expected.length, items.size(), "列表项数量不符");
        for (int i = 0; i < expected.length; i++) {
            MockedCandidate candidate = expected[i];
            JsonNode actual = items.get(i);
            Boolean passed = candidate.staticChecks().isEmpty() ? null
                : candidate.staticChecks().stream().allMatch(check -> check.checkStatus() == StaticCheckStatus.PASSED);
            assertAll("列表项 " + candidate.releaseId(),
                () -> assertEquals(Long.toString(candidate.releaseId()), actual.path("releaseId").asString()),
                () -> assertEquals(candidate.releaseNo(), actual.path("releaseNo").asInt()),
                () -> assertEquals(Long.toString(RuleSetBuilder.DECISION_POINT_ID),
                    actual.path("decisionPointId").asString()),
                () -> assertEquals(RuleSetBuilder.POINT_CODE, actual.path("pointCode").asString()),
                () -> assertEquals(Long.toString(candidate.composition().pointVersionId()),
                    actual.path("pointVersionId").asString()),
                () -> assertEquals(Long.toString(candidate.composition().ruleSetVersionId()),
                    actual.path("ruleSetVersionId").asString()),
                () -> assertEquals(DecisionEnvironment.TEST.getCode(), actual.path("environmentCode").asString()),
                () -> assertEquals(candidate.status().getCode(), actual.path("status").asString()),
                () -> assertEquals(candidate.version(), actual.path("version").asInt()),
                () -> assertEquals(candidate.contentHash(), actual.path("contentHash").asString()),
                () -> assertEquals(Long.toString(candidate.createdBy()), actual.path("createdBy").asString()),
                () -> assertEquals(time(candidate.createTime()), actual.path("createTime").asString()),
                () -> assertBoolean(passed, actual.path("allStaticChecksPassed")),
                () -> assertEquals(13, actual.size(), "列表项字段数量"));
        }
        return this;
    }
}
