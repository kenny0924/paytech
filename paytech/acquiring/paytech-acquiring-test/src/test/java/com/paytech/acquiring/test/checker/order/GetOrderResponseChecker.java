package com.paytech.acquiring.test.checker.order;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.acquiring.api.client.v1.order.response.GetOrderResponseBody;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.ObjectMapper;

/** 业务字段通过正式响应对象断言，响应结构遵循 DTO 的序列化定义。 */
public final class GetOrderResponseChecker extends AbstractHttpResponseChecker<GetOrderResponseChecker> {
    private final ObjectMapper mapper;
    private GetOrderResponseBody responseBody;

    /** 复用公共 HTTP/Problem 断言，调用方传入本例 Request ID。 */
    public GetOrderResponseChecker(EntityExchangeResult<byte[]> response, ObjectMapper mapper, String requestId) {
        super(response, mapper, requestId);
        this.mapper = mapper;
    }

    /** 保持链式断言的具体类型。 */
    @Override
    protected GetOrderResponseChecker self() {
        return this;
    }

    /** 业务断言使用正式响应对象，每个 HTTP 结果只反序列化一次。 */
    public GetOrderResponseBody getResponseBody() {
        if (responseBody == null) {
            responseBody = mapper.readValue(result.getResponseBody(), GetOrderResponseBody.class);
        }
        return responseBody;
    }

    /** DTO 定义字段和序列化形式，不再手写一套字段名、类型和可空字段清单。 */
    public GetOrderResponseChecker assertSuccess() {
        assertStatus(200).assertContentType(MediaType.APPLICATION_JSON).assertRequestId();
        var response = getResponseBody();
        assertNotNull(response.getItems());
        assertNotNull(response.getAmounts());
        assertNotNull(response.getRefundSummary());
        assertNotNull(response.getFundsAvailability());
        assertEquals(mapper.valueToTree(response), body(), "响应 JSON 应符合 ResponseBody 的序列化定义");
        return this;
    }

    /** 同时比较 DTO 金额数值及币种主单位格式，期望仍由场景明确给出。 */
    public static void assertAmount(String expected, String actual) {
        assertEquals(expected, actual, "金额格式");
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual)), "金额数值");
    }

    /** 固定两行展示顺序、数量精度、描述缺省与整行优惠。 */
    public GetOrderResponseChecker assertItems() {
        var items = getResponseBody().getItems();
        assertEquals(2, items.size());
        var first = items.getFirst();
        assertAll(() -> assertEquals("first", first.getClientItemId()), () -> assertEquals("first", first.getName()),
            () -> assertEquals("", first.getDescription()), () -> assertEquals("2.5", first.getQuantity()),
            () -> assertAmount("10.00", first.getUnitAmount()), () -> assertAmount("2.00", first.getDiscountAmount()),
            () -> assertEquals("SERVICE", first.getProductTypeCode()),
            () -> assertEquals("STANDARD", first.getTaxCategoryCode()));
        var second = items.get(1);
        assertAll(() -> assertEquals("second", second.getClientItemId()),
            () -> assertEquals("Second item", second.getDescription()), () -> assertEquals("1.25", second.getQuantity()),
            () -> assertAmount("20.00", second.getUnitAmount()), () -> assertAmount("0.00", second.getDiscountAmount()));
        return this;
    }

    /** 系统错误不得将内部校验消息、SQL 或异常类型透出给 Client。 */
    public GetOrderResponseChecker assertInternalError() {
        assertProblem(500, "INTERNAL_ERROR");
        var detail = mapper.readValue(result.getResponseBody(), ApiProblemResponse.class).detail();
        for (var forbidden : Set.of("t_order", "t_commerce", "Exception", "退款金额", "投影", "公开 URL")) {
            assertFalse(detail.contains(forbidden), "错误响应泄漏内部详情");
        }
        return this;
    }
}
