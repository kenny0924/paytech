package com.paytech.risk.test.checker.control;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 限额档位接口的 HTTP 响应 Checker
 * <li>文本字段按 JSON Pointer 读取，缺失与 null 都按 null 比较。</li>
 */
public final class LimitTierResponseChecker extends AbstractHttpResponseChecker<LimitTierResponseChecker> {

    public LimitTierResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected LimitTierResponseChecker self() {
        return this;
    }

    /**
     * 变更记录的状态、迁移与审批级别
     *
     * @param httpStatus 期望 HTTP 状态
     * @param status 期望申请状态 Code
     * @param fromTier 期望变更前档位 Code
     * @param toTier 期望目标档位 Code
     * @param approvalLevel 期望审批级别 Code；建档与迁移为 null
     * @return 当前 Checker
     */
    public LimitTierResponseChecker assertChange(int httpStatus, String status, String fromTier, String toTier,
        String approvalLevel) {
        assertStatus(httpStatus);
        assertAll("档位变更记录",
            () -> assertEquals(status, text("/status"), "status"),
            () -> assertEquals(fromTier, text("/fromTier"), "fromTier"),
            () -> assertEquals(toTier, text("/toTier"), "toTier"),
            () -> assertEquals(approvalLevel, text("/approvalLevel"), "approvalLevel"));
        return this;
    }

    /**
     * 按 JSON Pointer 断言文本值；期望 null 时字段须为 null 或缺失
     *
     * @param pointer JSON Pointer
     * @param expected 期望文本
     * @return 当前 Checker
     */
    public LimitTierResponseChecker assertText(String pointer, String expected) {
        assertEquals(expected, text(pointer), pointer);
        return this;
    }

    /**
     * 分页总数与本页某字段的值序列
     *
     * @param total 期望总数
     * @param field 每行字段名
     * @param values 期望值，按行顺序
     * @return 当前 Checker
     */
    public LimitTierResponseChecker assertPage(long total, String field, List<String> values) {
        assertStatus(200);
        List<String> actual = new ArrayList<>();
        body().path("items").forEach(item -> actual.add(item.path(field).isNull() ? null : item.path(field).asString()));
        assertAll("分页", () -> assertEquals(total, body().path("total").asLong(), "total"),
            () -> assertEquals(values, actual, field));
        return this;
    }

    /**
     * 非分页列表 items 中某字段的值序列
     *
     * @param field 每行字段名
     * @param values 期望值，按行顺序
     * @return 当前 Checker
     */
    public LimitTierResponseChecker assertItems(String field, List<String> values) {
        assertStatus(200);
        List<String> actual = new ArrayList<>();
        body().path("items").forEach(item -> actual.add(item.path(field).isNull() ? null : item.path(field).asString()));
        assertEquals(values, actual, field);
        return this;
    }

    /** JSON Pointer 处的文本；null 或缺失为 null。 */
    public String text(String pointer) {
        JsonNode node = body().at(pointer);
        return node.isNull() || node.isMissingNode() ? null : node.asString();
    }

    /** JSON Pointer 处的节点。 */
    public JsonNode node(String pointer) {
        return body().at(pointer);
    }

    /** 变更记录 ID。 */
    public long changeId() {
        assertFalse(body().path("changeId").isMissingNode(), "changeId");
        return Long.parseLong(body().path("changeId").asString());
    }

    /** 并发版本。 */
    public int version() {
        return body().path("version").asInt();
    }
}
