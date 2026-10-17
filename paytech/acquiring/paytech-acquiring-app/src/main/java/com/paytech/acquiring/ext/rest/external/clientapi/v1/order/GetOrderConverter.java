package com.paytech.acquiring.ext.rest.external.clientapi.v1.order;

import java.math.BigDecimal;

import com.paytech.acquiring.api.client.v1.order.response.GetOrderResponseBody;
import com.paytech.acquiring.api.client.v1.order.response.GetOrderResponseBody.*;
import com.paytech.acquiring.domain.order.CommerceDecimalRules;
import com.paytech.acquiring.service.processor.order.request.GetOrderRequest;
import com.paytech.acquiring.service.processor.order.response.GetOrderResponse;

/** 订单查询的 HTTP 模型转换；不读取数据库或计算业务状态。 */
public final class GetOrderConverter {
    /** 禁止实例化无状态转换器。 */
    private GetOrderConverter() {}

    /** 组合可信 Client 和原 Path/Query，参数位置保持不变。 */
    public static GetOrderRequest request(long clientId, String orderNo, String clientOrderId) {
        return new GetOrderRequest(clientId, orderNo, clientOrderId);
    }

    /** 公开业务字段及关联会话，内部版本不进入响应，金额仍按币种精度输出。 */
    public static GetOrderResponseBody response(GetOrderResponse result) {
        var currency = result.currency();
        var amounts = result.amounts();
        var refund = result.refundSummary();
        var funds = result.fundsAvailability();
        var checkout = result.checkout();
        var items = result.items().stream().map(item -> new OrderItemInfo(item.clientItemId(), item.name(),
            item.description() == null ? "" : item.description(), item.productTypeCode(), item.taxCategoryCode(),
            item.quantity().stripTrailingZeros().toPlainString(), money(currency, item.unitAmount()),
            money(currency, item.discountAmount()))).toList();
        return new GetOrderResponseBody(result.orderNo(), result.clientOrderId(), result.creationMode().getCode(),
            result.orderStatus().getCode(), result.ready() ? "READY" : "PENDING",
            result.finalAmount() ? "FINAL" : "ESTIMATED", result.paymentStatus().getCode(), currency,
            result.consumerCountryCode(), items,
            new AmountsInfo(money(currency, amounts.merchandiseAmount()), money(currency, amounts.discountAmount()),
                money(currency, amounts.netAmount()), money(currency, amounts.taxAmount()),
                money(currency, amounts.roundingAmount()), money(currency, amounts.totalAmount()),
                money(currency, amounts.paidAmount())),
            checkout == null ? null : new CheckoutInfo(checkout.status().getCode(), checkout.url(), checkout.expiresAt()),
            result.lastAttemptStatus() == null ? null : new LastPaymentAttemptInfo(result.lastAttemptStatus().getCode()),
            new RefundSummaryInfo(money(currency, refund.processingAmount()), money(currency, refund.succeededAmount()),
                money(currency, refund.refundableAmount())),
            new FundsAvailabilityInfo(funds.status().getCode(), money(currency, funds.availableAmount()),
                funds.scheduledAt(), funds.availableAt(), funds.asOf(), funds.updatedAt()),
            result.createdAt(), result.expiresAt(), result.paidAt(), result.updatedAt());
    }

    /** 使用公共 Money 输出币种主单位字符串；损坏的持久化金额按系统错误处理。 */
    static String money(String currency, BigDecimal value) {
        return CommerceDecimalRules.toAmountString(currency, value);
    }
}
