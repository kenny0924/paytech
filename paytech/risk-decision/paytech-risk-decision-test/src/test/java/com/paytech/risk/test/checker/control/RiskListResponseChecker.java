package com.paytech.risk.test.checker.control;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 运营名单维护的 HTTP 响应 Checker
 * <li>核对名单、名单成员与分页响应；成员主体键只以脱敏形式出现。</li>
 *
 * @author Kenny
 */
public final class RiskListResponseChecker extends AbstractHttpResponseChecker<RiskListResponseChecker> {

    public RiskListResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected RiskListResponseChecker self() {
        return this;
    }

    /**
     * 名单创建成功
     *
     * @param listCode 名单编码
     * @param subjectType 主体类型 Code
     * @param purpose 用途 Code
     * @return 名单 ID
     */
    public long assertList(String listCode, String subjectType, String purpose) {
        assertStatus(200);
        JsonNode body = body();
        assertAll("名单",
            () -> assertTrue(body.path("listId").asString().matches("[1-9][0-9]{0,18}"), "listId"),
            () -> assertEquals(listCode, body.path("listCode").asString(), "listCode"),
            () -> assertEquals(subjectType, body.path("subjectType").asString(), "subjectType"),
            () -> assertEquals(purpose, body.path("purpose").asString(), "purpose"),
            () -> assertTrue(body.path("active").asBoolean(), "active"));
        return Long.parseLong(body.path("listId").asString());
    }

    /**
     * 名单成员
     *
     * @param status 成员状态 Code
     * @param maskedSubjectKey 期望的脱敏主体键
     * @param sourceType 来源 Code
     * @return 成员 ID
     */
    public long assertEntry(String status, String maskedSubjectKey, String sourceType) {
        assertStatus(200);
        JsonNode body = body();
        assertAll("名单成员",
            () -> assertTrue(body.path("entryId").asString().matches("[1-9][0-9]{0,18}"), "entryId"),
            () -> assertEquals(status, body.path("status").asString(), "status"),
            () -> assertEquals(maskedSubjectKey, body.path("maskedSubjectKey").asString(), "maskedSubjectKey"),
            () -> assertEquals(sourceType, body.path("sourceType").asString(), "sourceType"));
        return Long.parseLong(body.path("entryId").asString());
    }

    /**
     * 已失效成员的失效人与原因
     *
     * @param deactivatedBy 失效人 Account ID
     * @param reason 失效原因
     * @return 当前 Checker
     */
    public RiskListResponseChecker assertDeactivated(long deactivatedBy, String reason) {
        JsonNode body = body();
        assertAll("失效审计",
            () -> assertEquals(Long.toString(deactivatedBy), body.path("deactivatedBy").asString(), "deactivatedBy"),
            () -> assertEquals(reason, body.path("deactivatedReason").asString(), "deactivatedReason"));
        return this;
    }

    /**
     * 分页结果
     *
     * @param total 总数
     * @param field 每项取值的字段名
     * @param values 期望的按序取值
     * @return 当前 Checker
     */
    public RiskListResponseChecker assertPage(long total, String field, List<String> values) {
        assertStatus(200);
        JsonNode body = body();
        List<String> actual = new ArrayList<>();
        body.path("items").forEach(item -> actual.add(item.path(field).asString()));
        assertAll("分页",
            () -> assertEquals(total, body.path("total").asLong(), "total"),
            () -> assertEquals(values, actual, field));
        return this;
    }
}
