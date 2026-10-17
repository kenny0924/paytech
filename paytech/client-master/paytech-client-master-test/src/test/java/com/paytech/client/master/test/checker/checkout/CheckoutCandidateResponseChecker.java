package com.paytech.client.master.test.checker.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 候选、复核、参与方资料与已存卡开关响应的业务断言。 */
public class CheckoutCandidateResponseChecker extends AbstractHttpResponseChecker<CheckoutCandidateResponseChecker> {
    /** 保存实际 HTTP 结果和本例预期 Request ID。 */
    public CheckoutCandidateResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    /** 返回当前协议检查器。 */
    @Override
    protected CheckoutCandidateResponseChecker self() {
        return this;
    }

    /** 成功响应：200 且 Request ID 一致。 */
    public CheckoutCandidateResponseChecker assertOk() {
        return assertStatus(200).assertRequestId();
    }

    /** 候选按给定顺序返回。 */
    public CheckoutCandidateResponseChecker assertCandidates(String... entityNos) {
        var candidates = body().path("candidates");
        assertTrue(candidates.isArray(), "缺少候选数组");
        assertEquals(List.of(entityNos), candidates.valueStream().map(c -> c.path("entityNo").asString()).toList());
        return this;
    }

    /** 首个候选的法律文件与卖方披露数量。 */
    public CheckoutCandidateResponseChecker assertFirstCandidateLegal(int documents, int sellers) {
        var first = body().path("candidates").get(0);
        assertEquals(documents, first.path("legalDocuments").size());
        assertEquals(sellers, first.path("sellerDisclosures").size());
        assertTrue(first.path("candidateDigest").asString("").matches("[0-9a-f]{64}"), "候选摘要必须为 SHA-256");
        return this;
    }

    /** 某类根的记录 ID 与修订。 */
    public CheckoutCandidateResponseChecker assertRoot(String group, String recordId, long version) {
        var roots = body().path("rootVersions").path(group);
        var match = roots.isArray()
            ? roots.valueStream().filter(r -> recordId.equals(r.path("recordId").asString())).findFirst().orElse(null)
            : roots;
        assertTrue(match != null && recordId.equals(match.path("recordId").asString()), "缺少根: " + group + ":" + recordId);
        assertEquals(version, match.path("version").asLong());
        return this;
    }

    /** 某个字段路径的文本值。 */
    public CheckoutCandidateResponseChecker assertText(String pointer, String expected) {
        assertEquals(expected, body().at(pointer).asString(null), "字段不符: " + pointer);
        return this;
    }

    /** 某个字段路径的布尔值。 */
    public CheckoutCandidateResponseChecker assertBoolean(String pointer, boolean expected) {
        assertEquals(expected, body().at(pointer).asBoolean(!expected), "字段不符: " + pointer);
        return this;
    }

    /** 某个数组字段的文本元素。 */
    public CheckoutCandidateResponseChecker assertTexts(String pointer, String... expected) {
        assertEquals(List.of(expected), body().at(pointer).valueStream().map(JsonNode::asString).toList());
        return this;
    }

    /** 错误响应不暴露数据库枚举或 Java 类型。 */
    public CheckoutCandidateResponseChecker assertDoesNotExpose(String value) {
        assertFalse(body().toString().contains(value));
        return this;
    }

    /** 原始 JSON，供复核用例回传首次结果。 */
    public JsonNode json() {
        return body();
    }
}
