package com.paytech.channel.connector.test.checker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 渠道成本 HTTP 响应断言：复用公共状态、Request ID 与 Problem 检查，补充 JSON 字段读取和业务拒绝断言。 */
public final class ChannelCostResponseChecker extends AbstractHttpResponseChecker<ChannelCostResponseChecker> {

    /** 包装一次真实 HTTP 交换结果。 */
    public ChannelCostResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    /** 保持链式断言返回当前 Checker。 */
    @Override
    protected ChannelCostResponseChecker self() {
        return this;
    }

    /** 断言成功状态、JSON 媒体类型与 Request ID。 */
    public ChannelCostResponseChecker assertSuccess(int status) {
        return assertStatus(status).assertContentType(MediaType.APPLICATION_JSON).assertRequestId();
    }

    /** 断言 400 业务拒绝，且提示包含给定业务上下文。 */
    public ChannelCostResponseChecker assertBusinessRejected(String detailFragment) {
        assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode());
        return assertDetailContains(detailFragment);
    }

    /** 断言 400 参数错误。 */
    public ChannelCostResponseChecker assertInvalidRequest() {
        assertProblem(400, CommonErrorCode.INVALID_REQUEST.getCode());
        return this;
    }

    /** 断言 Problem detail 包含给定片段。 */
    public ChannelCostResponseChecker assertDetailContains(String fragment) {
        String detail = body().path("detail").asString();
        assertTrue(detail.contains(fragment), "Problem detail 应包含 [" + fragment + "]，实际：" + detail);
        return this;
    }

    /** 断言字符串字段值。 */
    public ChannelCostResponseChecker assertText(String field, String expected) {
        assertEquals(expected, text(field), field);
        return this;
    }

    /** 断言时间字段按 Instant 数值相等；expected 为 null 表示字段为空。 */
    public ChannelCostResponseChecker assertInstant(String field, Instant expected) {
        String actual = text(field);
        assertEquals(expected, actual == null ? null : Instant.parse(actual), field);
        return this;
    }

    /** 读取字符串字段；缺失或为 null 时返回 null。 */
    public String text(String field) {
        var node = body().path(field);
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }

    /** 读取完整 JSON 响应体，用于嵌套集合断言。 */
    public JsonNode json() {
        return body();
    }
}
