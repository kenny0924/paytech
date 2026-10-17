package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedVersion;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 规则集历史分页的 HTTP 响应 Checker
 * <li>逐字段核对历史项与插入的版本事实；当前标记以规则集根的当前指针为准。</li>
 *
 * @author Kenny
 */
public final class RuleSetHistoryResponseChecker extends AbstractHttpResponseChecker<RuleSetHistoryResponseChecker> {

    /** 合同时间格式：UTC、固定六位小数。 */
    static final DateTimeFormatter TIME =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    public RuleSetHistoryResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected RuleSetHistoryResponseChecker self() {
        return this;
    }

    /** 成功响应及分页元数据。 */
    public RuleSetHistoryResponseChecker assertPage(int page, int size, long total) {
        assertStatus(200);
        JsonNode body = body();
        assertAll(() -> assertEquals(page, body.path("page").asInt(), "page"),
            () -> assertEquals(size, body.path("size").asInt(), "size"),
            () -> assertEquals(total, body.path("total").asLong(), "total"));
        return this;
    }

    /** 当前页按给定业务版本号顺序返回，逐项核对全部字段。 */
    public RuleSetHistoryResponseChecker assertVersions(MockedRuleSet ruleSet, int... versionNos) {
        JsonNode items = body().path("items");
        assertEquals(versionNos.length, items.size(), "历史项数量不符");
        for (int i = 0; i < versionNos.length; i++) {
            MockedVersion expected = ruleSet.version(versionNos[i]);
            JsonNode actual = items.get(i);
            assertAll("历史项 v" + versionNos[i],
                () -> assertEquals(Long.toString(ruleSet.ruleSetId()), actual.path("ruleSetId").asString()),
                () -> assertEquals(Long.toString(expected.ruleSetVersionId()),
                    actual.path("ruleSetVersionId").asString()),
                () -> assertEquals(RuleSetBuilder.POINT_CODE, actual.path("pointCode").asString()),
                () -> assertEquals(ruleSet.ruleSetCode(), actual.path("ruleSetCode").asString()),
                () -> assertEquals(expected.spec().ruleSetName(), actual.path("ruleSetName").asString()),
                () -> assertEquals(expected.versionNo(), actual.path("versionNo").asInt()),
                () -> assertEquals(expected.spec().status().getCode(), actual.path("status").asString()),
                () -> assertEquals(expected.ruleSetVersionId() == ruleSet.currentVersionId(),
                    actual.path("isCurrent").asBoolean()),
                () -> assertEquals(Long.toString(expected.spec().createdBy()), actual.path("createdBy").asString()),
                () -> assertEquals(expected.spec().changeReason(), actual.path("changeReason").asString()),
                () -> assertEquals(time(expected.spec().createTime()), actual.path("createTime").asString()));
        }
        return this;
    }

    /** 规则集不存在或已整体删除。 */
    public RuleSetHistoryResponseChecker assertNotFound() {
        return assertProblem(404, RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());
    }

    static String time(Instant value) {
        return value == null ? null : TIME.format(value);
    }
}
