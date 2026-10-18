package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.test.checker.decision.RuleSetListResponseChecker.text;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 规则集保存、冻结与整体删除命令的 HTTP 响应 Checker
 * <li>逐字段核对回执；服务端生成的版本 ID 只校验为正 BIGINT 文本，命令时间须为 UTC 微秒且落在请求窗口内。</li>
 * <li>{@link #ruleSetVersionId()}、{@link #commandTime()} 等只读访问器供数据库 Checker 关联持久化事实。</li>
 *
 * @author Kenny
 */
public final class RuleSetCommandResponseChecker
    extends AbstractHttpResponseChecker<RuleSetCommandResponseChecker> {

    private static final String POSITIVE_ID = "[1-9][0-9]{0,18}";

    private static final String HASH = "[0-9a-f]{64}";

    private final Instant requestedAfter;

    private final Instant respondedBefore;

    /** 记录请求前后时刻，用于核对命令时间。 */
    public RuleSetCommandResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId,
        Instant requestedAfter, Instant respondedBefore) {
        super(result, mapper, requestId);
        this.requestedAfter = requestedAfter.truncatedTo(ChronoUnit.MICROS);
        this.respondedBefore = respondedBefore;
    }

    @Override
    protected RuleSetCommandResponseChecker self() {
        return this;
    }

    /** 保存成功：新 DRAFT 版本，无旧编排坐标。 */
    public RuleSetCommandResponseChecker assertCreated(String ruleSetCode, int versionNo, long createdBy) {
        assertStatus(200);
        JsonNode body = body();
        assertAll("保存回执",
            () -> assertEquals(8, body.size(), "字段数量"),
            () -> assertTrue(body.path("ruleSetVersionId").asString("").matches(POSITIVE_ID), "ruleSetVersionId"),
            () -> assertEquals(null, text(body, "pointVersionId")),
            () -> assertEquals(ruleSetCode, body.path("ruleSetCode").asString()),
            () -> assertEquals(versionNo, body.path("versionNo").asInt()),
            () -> assertEquals(RuleSetVersionStatus.DRAFT.getCode(), body.path("status").asString()),
            () -> assertTrue(body.path("contentHash").asString("").matches(HASH), "contentHash"),
            () -> assertEquals(Long.toString(createdBy), body.path("createdBy").asString()),
            () -> assertCommandTime(body.path("createTime")));
        return this;
    }

    /** 冻结成功：同一版本变为 FROZEN，内容摘要不变。 */
    public RuleSetCommandResponseChecker assertFrozen(String ruleSetCode, long ruleSetVersionId, int versionNo,
        String contentHash, long frozenBy) {
        assertStatus(200);
        JsonNode body = body();
        assertAll("冻结回执",
            () -> assertEquals(8, body.size(), "字段数量"),
            () -> assertEquals(Long.toString(ruleSetVersionId), body.path("ruleSetVersionId").asString()),
            () -> assertEquals(null, text(body, "pointVersionId")),
            () -> assertEquals(ruleSetCode, body.path("ruleSetCode").asString()),
            () -> assertEquals(versionNo, body.path("versionNo").asInt()),
            () -> assertEquals(RuleSetVersionStatus.FROZEN.getCode(), body.path("status").asString()),
            () -> assertEquals(contentHash, body.path("contentHash").asString()),
            () -> assertEquals(Long.toString(frozenBy), body.path("frozenBy").asString()),
            () -> assertCommandTime(body.path("frozenTime")));
        return this;
    }

    /** 整体删除成功：DELETED 及递增后的管理版本。 */
    public RuleSetCommandResponseChecker assertDeleted(long ruleSetId, long version, long deletedBy) {
        assertStatus(200);
        JsonNode body = body();
        assertAll("删除回执",
            () -> assertEquals(5, body.size(), "字段数量"),
            () -> assertEquals(Long.toString(ruleSetId), body.path("ruleSetId").asString()),
            () -> assertEquals(RuleSetManagementStatus.DELETED.getCode(), body.path("status").asString()),
            () -> assertEquals(Long.toString(version), body.path("version").asString()),
            () -> assertEquals(Long.toString(deletedBy), body.path("deletedBy").asString()),
            () -> assertCommandTime(body.path("deletedTime")));
        return this;
    }

    /** 原始响应文本，用于确认错误响应不回显敏感输入。 */
    public String rawBody() {
        byte[] content = result.getResponseBody();
        return content == null ? "" : new String(content, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 保存回执中的新版本 ID。 */
    public long ruleSetVersionId() {
        return Long.parseLong(body().path("ruleSetVersionId").asString());
    }

    /** 保存回执中的内容摘要。 */
    public String contentHash() {
        return body().path("contentHash").asString();
    }

    /** 回执中的命令时间（createTime、frozenTime 或 deletedTime）。 */
    public Instant commandTime() {
        JsonNode body = body();
        String field = body.has("createTime") ? "createTime" : body.has("frozenTime") ? "frozenTime" : "deletedTime";
        return Instant.parse(body.path(field).asString());
    }

    private void assertCommandTime(JsonNode node) {
        String raw = node.asString("");
        assertTrue(raw.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}Z"), "命令时间格式：" + raw);
        Instant value = Instant.parse(raw);
        assertFalse(value.isBefore(requestedAfter), "命令时间早于请求");
        assertFalse(value.isAfter(respondedBefore), "命令时间晚于响应");
    }
}
