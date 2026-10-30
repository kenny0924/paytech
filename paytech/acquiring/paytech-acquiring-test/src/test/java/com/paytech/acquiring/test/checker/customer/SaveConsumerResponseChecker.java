package com.paytech.acquiring.test.checker.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.HashSet;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import com.paytech.acquiring.api.client.v1.enums.OrderAmountStatus;
import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 保存响应校验：固定字段集合防止回显个人资料，报价字段逐项比较。 */
public final class SaveConsumerResponseChecker extends AbstractHttpResponseChecker<SaveConsumerResponseChecker> {
    /** 复用 Foundation HTTP 和 Problem 断言。 */
    public SaveConsumerResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    /** 返回当前 Checker。 */
    @Override
    protected SaveConsumerResponseChecker self() {
        return this;
    }

    /** 比较保存状态，并拒绝多返回任何个人资料或保护引用：字段集合必须恰好是正式响应的字段。 */
    public SaveConsumerResponseChecker assertSaved(String orderNo) {
        assertStatus(200).assertContentType(MediaType.APPLICATION_JSON).assertRequestId();
        var body = body();
        assertEquals(Set.of("orderNo", "consumerStatus", "amountStatus", "taxBlockReason", "currency", "amounts"),
            fieldNames(body));
        assertEquals(orderNo, body.get("orderNo").asString());
        assertEquals("COMPLETE", body.get("consumerStatus").asString());
        return this;
    }

    /**
     * 比较重算后的报价
     *
     * @param amountStatus 期望金额状态
     * @param taxAmount 期望税额；BLOCKED 时为 null
     * @param totalAmount 期望总额；BLOCKED 时为 null
     * @param blockReason 期望阻断原因；非 BLOCKED 为 null
     * @return 当前 Checker
     */
    public SaveConsumerResponseChecker assertQuote(OrderAmountStatus amountStatus, String taxAmount,
        String totalAmount, String blockReason) {
        var body = body();
        assertEquals(amountStatus.getCode(), text(body.get("amountStatus")));
        assertEquals(taxAmount, text(body.get("amounts").get("taxAmount")));
        assertEquals(totalAmount, text(body.get("amounts").get("totalAmount")));
        assertEquals(blockReason, text(body.get("taxBlockReason")));
        return this;
    }

    /** JSON 字符串字段；缺失或 null 返回 null。 */
    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asString();
    }

    /** JSON Object 的字段名集合。 */
    private static Set<String> fieldNames(JsonNode node) {
        var names = new HashSet<String>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
