package com.paytech.commerce.test.checker.refund;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.commerce.client.api.v1.refund.response.GetRefundResponseBody;
import com.paytech.commerce.core.dal.dataobject.refund.RefundRequest;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.ObjectMapper;

/** 退款详情使用正式 DTO 断言；业务期望来自用例前置事实，不复制 JSON 字段名。 */
public final class GetRefundResponseChecker extends AbstractHttpResponseChecker<GetRefundResponseChecker> {
    private final ObjectMapper mapper;

    /** 复用公共 HTTP、Request ID 和 Problem 检查。 */
    public GetRefundResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
        this.mapper = mapper;
    }

    /** 保持链式断言返回当前 Checker。 */
    @Override
    protected GetRefundResponseChecker self() {
        return this;
    }

    /** 金额输出由用例明确指定，其余业务字段与独立准备的数据库对象比较。 */
    public GetRefundResponseChecker assertRefund(RefundRequest expected, String orderNo, String amount) {
        assertStatus(200).assertContentType(MediaType.APPLICATION_JSON).assertRequestId();
        var actual = mapper.readValue(result.getResponseBody(), GetRefundResponseBody.class);
        assertAll(() -> assertEquals(expected.getRefundRequestNo(), actual.getRefundRequestNo()),
            () -> assertEquals(expected.getClientRefundId(), actual.getClientRefundId()),
            () -> assertEquals(expected.getRequestOrigin().getCode(), actual.getRequestOrigin()),
            () -> assertEquals(orderNo, actual.getOrderNo()), () -> assertEquals(amount, actual.getAmount()),
            () -> assertEquals(expected.getCurrency(), actual.getCurrency()),
            () -> assertEquals(expected.getRefundStatus().getCode(), actual.getRefundStatus()),
            () -> assertEquals(expected.getReasonCode(), actual.getReasonCode()),
            () -> assertEquals(expected.getStatusReasonCode(), actual.getStatusReasonCode()),
            () -> assertEquals(expected.getCreateTime(), actual.getCreatedAt()),
            () -> assertEquals(expected.getCompletedAt(), actual.getCompletedAt()),
            () -> assertEquals(expected.getUpdateTime(), actual.getUpdatedAt()),
            () -> assertEquals(expected.getVersion(), actual.getVersion()));
        assertEquals(mapper.valueToTree(actual), body(), "响应 JSON 应符合 ResponseBody 的序列化定义");
        return this;
    }

    /** 损坏的本库事实返回系统错误，不暴露校验消息、SQL 或退款原因原文。 */
    public GetRefundResponseChecker assertInternalError() {
        assertProblem(500, CommonErrorCode.INTERNAL_ERROR.getCode());
        var problem = mapper.readValue(result.getResponseBody(), ApiProblemResponse.class);
        assertEquals(CommonErrorCode.INTERNAL_ERROR.getMessage(), problem.detail());
        return this;
    }
}
