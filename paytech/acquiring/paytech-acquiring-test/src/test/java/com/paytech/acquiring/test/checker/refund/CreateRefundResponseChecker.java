package com.paytech.acquiring.test.checker.refund;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.acquiring.api.client.v1.refund.response.CreateRefundResponseBody;
import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.ObjectMapper;

/** 使用正式 DTO 验证不可变受理回执；JSON 只负责字段与类型合同。 */
public final class CreateRefundResponseChecker extends AbstractHttpResponseChecker<CreateRefundResponseChecker> {
    private final ObjectMapper mapper;

    public CreateRefundResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
        this.mapper = mapper;
    }

    @Override
    protected CreateRefundResponseChecker self() {
        return this;
    }

    public CreateRefundResponseBody getResponseBody() {
        return mapper.readValue(result.getResponseBody(), CreateRefundResponseBody.class);
    }

    /** 回执没有实时退款状态、内部 ID 或版本；Location 指向原公开退款。 */
    public CreateRefundResponseChecker assertAccepted(String orderNo, String reference, String amount,
        String currency, Instant createdAt) {
        assertStatus(201).assertContentType(MediaType.APPLICATION_JSON).assertRequestId();
        var response = getResponseBody();
        assertAll(() -> assertEquals(orderNo, response.getOrderNo()),
            () -> assertEquals(reference, response.getClientRefundId()),
            () -> assertEquals(amount, response.getAmount()), () -> assertEquals(currency, response.getCurrency()),
            () -> assertEquals(createdAt, response.getCreatedAt()),
            () -> assertNotNull(response.getRefundRequestNo()),
            () -> assertEquals("/api/v1/refunds/" + response.getRefundRequestNo(),
                result.getResponseHeaders().getFirst(HttpHeaders.LOCATION)));
        assertEquals(mapper.valueToTree(response), body(), "响应 JSON 应符合 ResponseBody 的序列化定义");
        return this;
    }

    /** 同内容重提返回原回执，逐字段比较 DTO 而不是 JSON 路径。 */
    public CreateRefundResponseChecker assertSameReceipt(CreateRefundResponseBody original) {
        assertAccepted(original.getOrderNo(), original.getClientRefundId(), original.getAmount(),
            original.getCurrency(), original.getCreatedAt());
        assertEquals(original.getRefundRequestNo(), getResponseBody().getRefundRequestNo());
        return this;
    }
}
