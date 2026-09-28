package com.paytech.client.contract.test.checker.checkout;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.ObjectMapper;

/** 失败响应只包含公共 Problem，不外泄部分商业候选。 */
public class CheckoutResolutionResponseChecker extends AbstractHttpResponseChecker<CheckoutResolutionResponseChecker> {
    /** 保存实际 HTTP 结果和本例预期 Request ID。 */
    public CheckoutResolutionResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper,
            String requestId) {
        super(result, mapper, requestId);
    }

    /** 返回当前协议检查器。 */
    @Override
    protected CheckoutResolutionResponseChecker self() {
        return this;
    }

    /** 不可用、冲突或非法请求不能夹带已选择的 OE 和合同信息。 */
    public CheckoutResolutionResponseChecker assertNoCommercialFacts() {
        assertFalse(body().has("entityNo"));
        assertFalse(body().has("clientTerms"));
        assertFalse(body().has("legal"));
        return this;
    }

    /** 错误响应不暴露数据库枚举、类型或内部版本标识。 */
    public CheckoutResolutionResponseChecker assertDoesNotExpose(String value) {
        assertFalse(body().toString().contains(value));
        return this;
    }
}
