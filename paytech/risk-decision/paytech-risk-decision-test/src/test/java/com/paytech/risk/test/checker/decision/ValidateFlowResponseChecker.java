package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.test.checker.decision.FlowGraphChecker.assertFields;
import static com.paytech.risk.test.checker.decision.FlowGraphChecker.text;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 流程只读静态校验的 HTTP 响应 Checker
 * <li>问题列表按返回顺序逐项核对代码、节点键、连线键、字段路径与安全文案。</li>
 *
 * @author Kenny
 */
public final class ValidateFlowResponseChecker extends AbstractHttpResponseChecker<ValidateFlowResponseChecker> {

    /** 一个期望的静态问题；未关联的坐标为 null。 */
    public record ExpectedIssue(String code, String nodeKey, String edgeKey, String field, String message) {
    }

    public ValidateFlowResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected ValidateFlowResponseChecker self() {
        return this;
    }

    /** 配置通过静态校验：没有问题，也没有截断。 */
    public ValidateFlowResponseChecker assertValid() {
        assertStatus(200);
        JsonNode body = body();
        assertFields(body, "valid", "issues", "truncated");
        assertAll(() -> assertTrue(body.path("valid").asBoolean(), "valid"),
            () -> assertEquals(0, body.path("issues").size(), "issues"),
            () -> assertFalse(body.path("truncated").asBoolean(), "truncated"));
        return this;
    }

    /** 配置未通过：问题按给定顺序完全一致，未截断。 */
    public ValidateFlowResponseChecker assertIssues(ExpectedIssue... expected) {
        assertStatus(200);
        JsonNode body = body();
        assertFields(body, "valid", "issues", "truncated");
        assertFalse(body.path("valid").asBoolean(), "valid");
        assertFalse(body.path("truncated").asBoolean(), "truncated");
        assertIssueList(body.path("issues"), List.of(expected));
        return this;
    }

    /** 问题超过上限：只返回前 100 项并标记截断，返回项均为给定代码。 */
    public ValidateFlowResponseChecker assertTruncated(String... codes) {
        assertStatus(200);
        JsonNode body = body();
        assertAll(() -> assertFalse(body.path("valid").asBoolean(), "valid"),
            () -> assertTrue(body.path("truncated").asBoolean(), "truncated"),
            () -> assertEquals(100, body.path("issues").size(), "issues 数量"));
        List<String> allowed = List.of(codes);
        body.path("issues").forEach(issue -> assertTrue(allowed.contains(text(issue.path("code"))),
            "意外问题代码：" + issue));
        return this;
    }

    private static void assertIssueList(JsonNode actual, List<ExpectedIssue> expected) {
        assertEquals(expected.size(), actual.size(), "问题数量不符：" + actual);
        for (int i = 0; i < expected.size(); i++) {
            ExpectedIssue issue = expected.get(i);
            JsonNode item = actual.get(i);
            assertFields(item, "code", "nodeKey", "edgeKey", "field", "message");
            assertAll("问题 " + i, () -> assertEquals(issue.code(), text(item.path("code")), "code"),
                () -> assertEquals(issue.nodeKey(), text(item.path("nodeKey")), "nodeKey"),
                () -> assertEquals(issue.edgeKey(), text(item.path("edgeKey")), "edgeKey"),
                () -> assertEquals(issue.field(), text(item.path("field")), "field"),
                () -> assertEquals(issue.message(), text(item.path("message")), "message"));
        }
    }
}
