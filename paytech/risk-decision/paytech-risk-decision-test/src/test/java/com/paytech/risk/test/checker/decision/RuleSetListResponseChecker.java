package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.test.checker.decision.RuleSetHistoryResponseChecker.time;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedVersion;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 规则集配置头分页的 HTTP 响应 Checker
 * <li>每个列表项是规则集当前版本，逐字段核对插入的版本事实、整体管理身份与版本、旧编排坐标及使用状态。</li>
 *
 * @author Kenny
 */
public final class RuleSetListResponseChecker extends AbstractHttpResponseChecker<RuleSetListResponseChecker> {

    /** 期望的一个列表项：规则集当前版本、旧编排坐标（无则 null）与是否被活动发布使用。 */
    public record Item(MockedRuleSet ruleSet, Long pointVersionId, boolean inUse) {
        /** 未发布、无旧编排的规则集。 */
        public static Item of(MockedRuleSet ruleSet) {
            return new Item(ruleSet, null, false);
        }
    }

    public RuleSetListResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected RuleSetListResponseChecker self() {
        return this;
    }

    /** 成功响应及分页元数据。 */
    public RuleSetListResponseChecker assertPage(int page, int size, long total) {
        assertStatus(200);
        JsonNode body = body();
        assertAll(() -> assertEquals(page, body.path("page").asInt(), "page"),
            () -> assertEquals(size, body.path("size").asInt(), "size"),
            () -> assertEquals(total, body.path("total").asLong(), "total"));
        return this;
    }

    /** 当前页按给定顺序返回这些规则集，逐项核对全部字段。 */
    public RuleSetListResponseChecker assertItems(Item... expected) {
        JsonNode items = body().path("items");
        assertTrue(items.isArray(), "items 必须为数组");
        assertEquals(expected.length, items.size(), "列表项数量不符");
        for (int i = 0; i < expected.length; i++) {
            assertItem(expected[i], items.get(i));
        }
        return this;
    }

    private static void assertItem(Item expected, JsonNode actual) {
        MockedRuleSet ruleSet = expected.ruleSet();
        MockedVersion current = ruleSet.current();
        boolean frozen = current.spec().status() == RuleSetVersionStatus.FROZEN;
        assertAll("列表项 " + ruleSet.ruleSetCode(),
            () -> assertEquals(16, actual.size(), "字段数量"),
            () -> assertEquals(Long.toString(current.ruleSetVersionId()), actual.path("ruleSetVersionId").asString()),
            () -> assertEquals(expected.pointVersionId() == null ? null : expected.pointVersionId().toString(),
                text(actual, "pointVersionId")),
            () -> assertEquals(RuleSetBuilder.POINT_CODE, actual.path("pointCode").asString()),
            () -> assertEquals(ruleSet.ruleSetCode(), actual.path("ruleSetCode").asString()),
            () -> assertEquals(current.spec().ruleSetName(), actual.path("ruleSetName").asString()),
            () -> assertEquals(current.versionNo(), actual.path("versionNo").asInt()),
            () -> assertEquals(current.spec().status().getCode(), actual.path("status").asString()),
            () -> assertEquals(current.contentHash(), actual.path("contentHash").asString()),
            () -> assertEquals(Long.toString(current.spec().createdBy()), actual.path("createdBy").asString()),
            () -> assertEquals(current.spec().changeReason(), actual.path("changeReason").asString()),
            () -> assertEquals(time(current.spec().createTime()), actual.path("createTime").asString()),
            () -> assertEquals(frozen ? Long.toString(current.spec().createdBy()) : null, text(actual, "frozenBy")),
            () -> assertEquals(frozen ? time(current.spec().createTime()) : null, text(actual, "frozenTime")),
            () -> assertEquals(Long.toString(ruleSet.ruleSetId()), actual.path("ruleSetId").asString()),
            () -> assertEquals("1", actual.path("managementVersion").asString()),
            () -> assertEquals(expected.inUse(), actual.path("inUse").asBoolean()));
    }

    /** 显式 null 映射为 Java null；键缺失视为错误。 */
    static String text(JsonNode node, String field) {
        assertTrue(node.has(field), "缺少字段：" + field);
        return node.get(field).isNull() ? null : node.get(field).asString();
    }
}
