package com.paytech.acquiring.test.checker.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import com.paytech.acquiring.api.checkout.v1.checkout.response.SaveConsumerResponseBody;
import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import tools.jackson.databind.ObjectMapper;

/** 使用正式响应对象校验，不维护 JSON 字段名集合。 */
public final class SaveConsumerResponseChecker extends AbstractHttpResponseChecker<SaveConsumerResponseChecker> {
    private final ObjectMapper mapper;

    /** 复用 Foundation HTTP 和 Problem 断言。 */
    public SaveConsumerResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
        this.mapper = mapper;
    }

    /** 返回当前 Checker。 */
    @Override
    protected SaveConsumerResponseChecker self() {
        return this;
    }

    /** 精确比较正式响应序列化结果，同时拒绝多返回任何个人资料或保护引用。 */
    public SaveConsumerResponseChecker assertSaved(String orderNo) {
        assertStatus(200).assertContentType(MediaType.APPLICATION_JSON).assertRequestId();
        var expected = new SaveConsumerResponseBody(orderNo, "COMPLETE");
        assertEquals(mapper.valueToTree(expected), body());
        return this;
    }
}
