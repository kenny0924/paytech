package com.paytech.acquiring.test.checker.checkout;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.acquiring.api.client.v1.checkout.response.CreateCheckoutResponseBody;
import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.ObjectMapper;

/** 只读 HTTP 响应检查，不准备数据、不触发业务命令。 */
public final class CreateCheckoutResponseChecker extends AbstractHttpResponseChecker<CreateCheckoutResponseChecker> {
    private final ObjectMapper mapper;
    private CreateCheckoutResponseBody responseBody;

    /** 公共基类管理 HTTP 结果及协议断言。 */
    public CreateCheckoutResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
        this.mapper = mapper;
    }

    /** 公共断言后可继续调用创建订单的业务断言。 */
    @Override
    protected CreateCheckoutResponseChecker self() {
        return this;
    }

    /** 业务断言复用正式响应对象，不使用字段名字符串访问。 */
    public CreateCheckoutResponseBody getResponseBody() {
        if (responseBody == null) {
            responseBody = mapper.readValue(result.getResponseBody(), CreateCheckoutResponseBody.class);
        }
        return responseBody;
    }

    /** 检查当前创建回执，缓存头由 Gateway 拥有，不在 Commerce 强求。 */
    public CreateCheckoutResponseChecker assertCreated(String clientOrderId, Instant expiresAt, String gross,
        String discount, String net) {
        assertStatus(201).assertContentType(MediaType.APPLICATION_JSON).assertRequestId();
        var response = getResponseBody();
        assertAll(() -> assertTrue(orderNo().matches("ord_[A-Za-z0-9]{30}")),
            () -> assertEquals(clientOrderId, response.getClientOrderId()),
            () -> assertEquals("USD", response.getCurrency()), () -> assertEquals(expiresAt, response.getExpiresAt()),
            () -> assertEquals("READY", response.getPreparationStatus()),
            () -> assertEquals("FINAL", response.getAmountStatus()),
            () -> assertEquals("OPEN", response.getCheckoutStatus()),
            () -> assertTrue(
                response.getCheckoutUrl().matches("https://checkout\\.local\\.test/c/chk_[A-Za-z0-9]{30}")),
            () -> assertEquals("/api/v1/orders/" + orderNo(),
                result.getResponseHeaders().getFirst(HttpHeaders.LOCATION)));
        var amounts = response.getAmounts();
        assertNotNull(amounts);
        assertAll(() -> assertEquals(gross, amounts.getMerchandiseAmount()),
            () -> assertEquals(discount, amounts.getDiscountAmount()), () -> assertEquals(net, amounts.getNetAmount()),
            () -> assertEquals("0.00",amounts.getTaxAmount()), () -> assertEquals("0.00",amounts.getRoundingAmount()),
            () -> assertEquals(net,amounts.getTotalAmount()));
        assertEquals(mapper.valueToTree(response), body(), "响应 JSON 应符合 ResponseBody 的序列化定义");
        return this;
    }

    /** 提供实际公开编号，供数据库检查定位；金额期望不能从本响应反推。 */
    public String orderNo() {
        return getResponseBody().getOrderNo();
    }

    /** 实际公开链接供数据库一致性检查使用。 */
    public String checkoutUrl() {
        return getResponseBody().getCheckoutUrl();
    }
}
