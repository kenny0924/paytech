package com.paytech.acquiring.test.checker.refund;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.acquiring.api.client.v1.refund.response.GetRefundableAmountResponseBody;
import com.paytech.acquiring.core.dal.dataobject.order.OrderRefundPosition;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.ObjectMapper;

/** 可退款金额使用正式响应对象断言，金额期望由用例独立指定。 */
public final class GetRefundableAmountResponseChecker
    extends AbstractHttpResponseChecker<GetRefundableAmountResponseChecker> {
    private final ObjectMapper mapper;

    /** 复用公共 HTTP、Request ID 和 Problem 检查。 */
    public GetRefundableAmountResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper,
        String requestId) {
        super(result, mapper, requestId);
        this.mapper = mapper;
    }

    /** 保持链式断言返回当前 Checker。 */
    @Override
    protected GetRefundableAmountResponseChecker self() {
        return this;
    }

    /** 时间、币种和版本取独立准备的位置事实，不以响应值反推期望。 */
    public GetRefundableAmountResponseChecker assertAmount(String orderNo, OrderRefundPosition expected,
        String amount) {
        assertStatus(200).assertContentType(MediaType.APPLICATION_JSON).assertRequestId();
        var actual = mapper.readValue(result.getResponseBody(), GetRefundableAmountResponseBody.class);
        assertAll(() -> assertEquals(orderNo, actual.getOrderNo()),
            () -> assertEquals(expected.getCurrency(), actual.getCurrency()),
            () -> assertEquals(amount, actual.getRefundableAmount()),
            () -> assertEquals(expected.getUpdateTime(), actual.getAsOf()),
            () -> assertEquals(expected.getVersion(), actual.getVersion()));
        assertEquals(mapper.valueToTree(actual), body(), "响应 JSON 应符合 ResponseBody 的序列化定义");
        return this;
    }

    /** 本库损坏事实不能伪装成零额或请求错误，且不对外泄露内部细节。 */
    public GetRefundableAmountResponseChecker assertInternalError() {
        assertProblem(500, CommonErrorCode.INTERNAL_ERROR.getCode());
        var problem = mapper.readValue(result.getResponseBody(), ApiProblemResponse.class);
        assertEquals(CommonErrorCode.INTERNAL_ERROR.getMessage(), problem.detail());
        return this;
    }
}
