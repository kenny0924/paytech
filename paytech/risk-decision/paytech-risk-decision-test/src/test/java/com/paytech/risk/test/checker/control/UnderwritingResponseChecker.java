package com.paytech.risk.test.checker.control;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 承保接口的 HTTP 响应 Checker
 * <li>核对版本详情、列表、预览与统计；文本字段按 JSON Pointer 读取，缺失与 null 都按 null 比较。</li>
 *
 * @author Claude
 */
public final class UnderwritingResponseChecker extends AbstractHttpResponseChecker<UnderwritingResponseChecker> {

    public UnderwritingResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected UnderwritingResponseChecker self() {
        return this;
    }

    /**
     * 版本详情的状态与复核级别
     *
     * @param httpStatus 期望 HTTP 状态
     * @param status 期望承保状态 Code
     * @param reviewLevel 期望复核级别 Code
     * @return 当前 Checker
     */
    public UnderwritingResponseChecker assertUnderwriting(int httpStatus, String status, String reviewLevel) {
        assertStatus(httpStatus);
        JsonNode body = body();
        assertAll("承保版本",
            () -> assertTrue(body.path("underwritingId").asString().matches("[1-9][0-9]{0,18}"), "underwritingId"),
            () -> assertEquals(status, body.path("status").asString(), "status"),
            () -> assertEquals(reviewLevel, body.path("reviewLevel").asString(), "reviewLevel"));
        return this;
    }

    /**
     * 按 JSON Pointer 断言文本值；期望 null 时字段须为 null 或缺失
     *
     * @param pointer JSON Pointer，例如 /decision/limitNote
     * @param expected 期望文本
     * @return 当前 Checker
     */
    public UnderwritingResponseChecker assertText(String pointer, String expected) {
        assertEquals(expected, text(pointer), pointer);
        return this;
    }

    /**
     * 估算敞口
     *
     * @param amount 期望金额文本
     * @param currency 期望币种
     * @param riskDays 期望风险天数
     * @param assumed 期望是否按 0 计结算周期
     * @return 当前 Checker
     */
    public UnderwritingResponseChecker assertExposure(String exposurePointer, String amount, String currency, int riskDays,
        boolean assumed) {
        JsonNode exposure = body().at(exposurePointer);
        assertAll("估算敞口",
            () -> assertEquals(amount, exposure.path("amount").asString(), "amount"),
            () -> assertEquals(currency, exposure.path("currency").asString(), "currency"),
            () -> assertEquals(riskDays, exposure.path("riskDays").asInt(), "riskDays"),
            () -> assertEquals(assumed, exposure.path("settlementDaysAssumed").asBoolean(), "settlementDaysAssumed"),
            () -> assertTrue(exposure.path("formula").asString().endsWith(" × " + riskDays + " ÷ 30"), "formula"));
        return this;
    }

    /**
     * 不可估算
     *
     * @param exposurePointer 估算敞口对象的位置
     * @param reasonPointer 原因字段的位置
     * @param reason 期望原因 Code
     * @return 当前 Checker
     */
    public UnderwritingResponseChecker assertExposureUnavailable(String exposurePointer, String reasonPointer, String reason) {
        assertTrue(body().at(exposurePointer).isNull() || body().at(exposurePointer).isMissingNode(), exposurePointer);
        return assertText(reasonPointer, reason);
    }

    /**
     * 预览的检查结果与是否可自动承保
     *
     * @param eligible 期望是否可自动承保
     * @param failedCodes 期望未通过的检查 Code（其余全部通过）
     * @return 当前 Checker
     */
    public UnderwritingResponseChecker assertChecks(boolean eligible, List<String> failedCodes) {
        assertStatus(200);
        JsonNode body = body();
        Map<String, Boolean> checks = new LinkedHashMap<>();
        body.path("checks").forEach(check -> checks.put(check.path("code").asString(), check.path("passed").asBoolean()));
        List<String> failed = new ArrayList<>();
        checks.forEach((code, passed) -> {
            if (!passed) {
                failed.add(code);
            }
        });
        assertAll("自动承保判定",
            () -> assertEquals(8, checks.size(), "检查项数量"),
            () -> assertEquals(eligible, body.path("autoEligible").asBoolean(), "autoEligible"),
            () -> assertEquals(failedCodes, failed, "未通过的检查"));
        return this;
    }

    /**
     * 列表各行的某个文本字段（按返回顺序）与总数
     *
     * @param total 期望总数
     * @param field 行内字段名
     * @param values 期望各行的值
     * @return 当前 Checker
     */
    public UnderwritingResponseChecker assertPage(long total, String field, List<String> values) {
        assertStatus(200);
        JsonNode body = body();
        List<String> actual = new ArrayList<>();
        body.path("items").forEach(item -> actual.add(item.path(field).isNull() ? null : item.path(field).asString()));
        assertAll("承保列表",
            () -> assertEquals(total, body.path("total").asLong(), "total"),
            () -> assertEquals(values, actual, field));
        return this;
    }

    /** 历史各版本的状态，版本号倒序。 */
    public UnderwritingResponseChecker assertHistory(List<String> statuses) {
        assertStatus(200);
        List<String> actual = new ArrayList<>();
        body().path("items").forEach(item -> actual.add(item.path("status").asString()));
        assertEquals(statuses, actual, "版本历史状态");
        return this;
    }

    /** 当前承保中三个位置的版本号；null 表示该位置为空。 */
    public UnderwritingResponseChecker assertCurrent(Integer effective, Integer open, Integer latest) {
        assertStatus(200);
        JsonNode body = body();
        assertAll("当前承保",
            () -> assertEquals(effective, versionNo(body.path("effective")), "effective"),
            () -> assertEquals(open, versionNo(body.path("open")), "open"),
            () -> assertEquals(latest, versionNo(body.path("latest")), "latest"));
        return this;
    }

    /** 统计五项计数。 */
    public UnderwritingResponseChecker assertStatistics(long effective, long draft, long submitted, long approved,
        long declined) {
        assertStatus(200);
        JsonNode body = body();
        assertAll("承保统计",
            () -> assertEquals(effective, body.path("effective").asLong(), "effective"),
            () -> assertEquals(draft, body.path("draft").asLong(), "draft"),
            () -> assertEquals(submitted, body.path("submitted").asLong(), "submitted"),
            () -> assertEquals(approved, body.path("approvedThisMonth").asLong(), "approvedThisMonth"),
            () -> assertEquals(declined, body.path("declinedThisMonth").asLong(), "declinedThisMonth"));
        return this;
    }

    /**
     * 某个对象的字段名集合（含值为 null 的字段）；Operations BFF 按精确键集合校验 Risk 成功响应
     *
     * @param pointer 对象位置，根为空串
     * @param names 期望的全部字段名
     * @return 当前 Checker
     */
    public UnderwritingResponseChecker assertFieldNames(String pointer, java.util.Set<String> names) {
        assertEquals(names, body().at(pointer).propertyNames(), "字段集合 " + pointer);
        return this;
    }

    /** JSON Pointer 处的文本；null 或缺失为 null。 */
    public String text(String pointer) {
        JsonNode node = body().at(pointer);
        return node.isNull() || node.isMissingNode() ? null : node.asString();
    }

    /** 承保版本 ID。 */
    public long id() {
        assertFalse(body().path("underwritingId").isMissingNode(), "underwritingId");
        return Long.parseLong(body().path("underwritingId").asString());
    }

    /** 并发版本。 */
    public int version() {
        return body().path("version").asInt();
    }

    /** 可空版本对象的版本号。 */
    private static Integer versionNo(JsonNode node) {
        return node.isNull() || node.isMissingNode() ? null : node.path("versionNo").asInt();
    }
}
