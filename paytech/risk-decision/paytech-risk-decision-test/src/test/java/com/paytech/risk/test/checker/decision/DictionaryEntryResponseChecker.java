package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode.RISK_DICTIONARY_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.TreeSet;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.decision.domain.enums.DictionaryType;
import com.paytech.risk.test.mocker.decision.DictionaryMocker.MockedDictionary;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 字典详情与当前值分页的 HTTP 响应 Checker
 * <li>逐字段核对原因或标签当前事实，字段集合必须与合同一致：标签不输出 defaultMessage，空说明输出 JSON null。</li>
 *
 * @author Kenny
 */
public final class DictionaryEntryResponseChecker extends AbstractHttpResponseChecker<DictionaryEntryResponseChecker> {

    static final Set<String> REASON_FIELDS = Set.of("reasonCode", "name", "description", "defaultMessage", "status",
        "version", "createdBy", "updatedBy", "createTime", "updateTime");

    static final Set<String> TAG_FIELDS = Set.of("tagCode", "name", "description", "status", "version", "createdBy",
        "updatedBy", "createTime", "updateTime");

    public DictionaryEntryResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected DictionaryEntryResponseChecker self() {
        return this;
    }

    /** 详情成功，内容与期望字典一致。 */
    public DictionaryEntryResponseChecker assertEntry(MockedDictionary expected) {
        assertStatus(200);
        assertEntry(expected, body());
        return this;
    }

    /** 分页成功及分页元数据。 */
    public DictionaryEntryResponseChecker assertPage(int page, int size, long total) {
        assertStatus(200);
        JsonNode body = body();
        assertAll(() -> assertEquals(Set.of("items", "page", "size", "total"), fields(body), "分页字段"),
            () -> assertEquals(page, body.path("page").asInt(), "page"),
            () -> assertEquals(size, body.path("size").asInt(), "size"),
            () -> assertEquals(total, body.path("total").asLong(), "total"));
        return this;
    }

    /** 当前页按给定顺序返回这些字典，逐项核对全部字段。 */
    public DictionaryEntryResponseChecker assertItems(MockedDictionary... expected) {
        JsonNode items = body().path("items");
        assertEquals(expected.length, items.size(), "当前页条数不符");
        for (int i = 0; i < expected.length; i++) {
            assertEntry(expected[i], items.get(i));
        }
        return this;
    }

    /** 当前页包含这些字典且相对顺序一致，用于不带筛选、可能含其他数据的列表。 */
    public DictionaryEntryResponseChecker assertContainsInOrder(MockedDictionary... expected) {
        JsonNode items = body().path("items");
        int from = 0;
        for (MockedDictionary entry : expected) {
            int found = -1;
            for (int i = from; i < items.size(); i++) {
                if (entry.code().equals(items.get(i).path(codeField(entry.type())).asString())) {
                    found = i;
                    break;
                }
            }
            assertTrue(found >= 0, "当前页缺少或乱序：" + entry.code());
            assertEntry(entry, items.get(found));
            from = found + 1;
        }
        return this;
    }

    /** 字典编码未登记。 */
    public DictionaryEntryResponseChecker assertNotFound() {
        return assertProblem(404, RISK_DICTIONARY_NOT_FOUND.getCode());
    }

    private static void assertEntry(MockedDictionary expected, JsonNode actual) {
        boolean reason = expected.type() == DictionaryType.REJECTION_REASON;
        assertAll("字典 " + expected.code(),
            () -> assertEquals(reason ? REASON_FIELDS : TAG_FIELDS, fields(actual), "字段集合"),
            () -> assertEquals(expected.code(), actual.path(codeField(expected.type())).asString()),
            () -> assertEquals(expected.name(), actual.path("name").asString()),
            () -> assertText(expected.description(), actual.path("description")),
            // 标签无默认文案字段，已由字段集合断言覆盖。
            () -> assertText(reason ? expected.defaultMessage() : null, reason ? actual.path("defaultMessage") : null),
            () -> assertEquals(expected.status().getCode(), actual.path("status").asString()),
            () -> assertEquals(Long.toString(expected.version()), actual.path("version").asString()),
            () -> assertTrue(actual.path("version").isString(), "version 必须为字符串"),
            () -> assertEquals(Long.toString(expected.createdBy()), actual.path("createdBy").asString()),
            () -> assertEquals(Long.toString(expected.updatedBy()), actual.path("updatedBy").asString()),
            () -> assertEquals(RuleSetHistoryResponseChecker.time(expected.createTime()),
                actual.path("createTime").asString()),
            () -> assertEquals(RuleSetHistoryResponseChecker.time(expected.updateTime()),
                actual.path("updateTime").asString()));
    }

    static String codeField(DictionaryType type) {
        return type == DictionaryType.REJECTION_REASON ? "reasonCode" : "tagCode";
    }

    /** 期望 null 时必须为 JSON null；否则为相同字符串。 */
    static void assertText(String expected, JsonNode actual) {
        if (expected == null) {
            assertTrue(actual == null || actual.isNull(), "应为 JSON null");
        } else {
            assertTrue(actual.isString(), "应为字符串");
            assertEquals(expected, actual.asString());
        }
    }

    static Set<String> fields(JsonNode node) {
        return new TreeSet<>(node.propertyNames());
    }
}
