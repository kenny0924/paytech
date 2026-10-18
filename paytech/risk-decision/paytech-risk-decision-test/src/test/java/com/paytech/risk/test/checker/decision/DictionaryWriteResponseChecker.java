package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode.RISK_DICTIONARY_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Set;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.decision.domain.enums.DictionaryStatus;
import com.paytech.risk.decision.domain.enums.DictionaryType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 字典创建、更新与启停的 HTTP 响应 Checker
 * <li>核对本次提交结果的全部字段；变更时间由服务端时钟生成，只断言格式及落在请求前后窗口内。</li>
 *
 * @author Kenny
 */
public final class DictionaryWriteResponseChecker extends AbstractHttpResponseChecker<DictionaryWriteResponseChecker> {

    static final Set<String> FIELDS = Set.of("dictionaryType", "code", "version", "status", "changedBy", "changedTime");

    public DictionaryWriteResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected DictionaryWriteResponseChecker self() {
        return this;
    }

    /** 成功响应：类型、编码、提交后修订号与状态、可信操作者。 */
    public DictionaryWriteResponseChecker assertWritten(DictionaryType type, String code, long version,
        DictionaryStatus status, long changedBy) {
        assertStatus(200);
        JsonNode body = body();
        assertAll("字典写入结果 " + code,
            () -> assertEquals(FIELDS, DictionaryEntryResponseChecker.fields(body), "字段集合"),
            () -> assertEquals(type.getCode(), body.path("dictionaryType").asString()),
            () -> assertEquals(code, body.path("code").asString()),
            () -> assertTrue(body.path("version").isString(), "version 必须为字符串"),
            () -> assertEquals(Long.toString(version), body.path("version").asString()),
            () -> assertEquals(status.getCode(), body.path("status").asString()),
            () -> assertEquals(Long.toString(changedBy), body.path("changedBy").asString()),
            () -> assertTrue(body.path("changedTime").asString("")
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}Z"), "changedTime 必须为 UTC 六位小数"));
        return this;
    }

    /** 变更时间落在请求发出与收到响应之间。 */
    public DictionaryWriteResponseChecker assertChangedBetween(Instant before, Instant after) {
        Instant changed = changedTime();
        assertFalse(changed.isBefore(before), "变更时间早于请求：" + changed);
        assertFalse(changed.isAfter(after), "变更时间晚于响应：" + changed);
        return this;
    }

    /** 本次变更时间，用于与数据库更新时间比对。 */
    public Instant changedTime() {
        return Instant.parse(body().path("changedTime").asString());
    }

    /** 并发竞争的失败方：409 Problem，业务码为允许集合之一（等锁超时或锁后发现冲突）。 */
    public DictionaryWriteResponseChecker assertConflictIn(String... allowedCodes) {
        assertStatus(409);
        String code = body().path("code").asString();
        assertTrue(Set.of(allowedCodes).contains(code), "冲突业务码不在预期范围：" + code);
        return assertProblem(409, code);
    }

    /** 字典编码未登记。 */
    public DictionaryWriteResponseChecker assertNotFound() {
        return assertProblem(404, RISK_DICTIONARY_NOT_FOUND.getCode());
    }
}
