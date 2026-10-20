package com.paytech.acquiring.integration.counterparty.impl;

import java.util.Arrays;

import org.springframework.stereotype.Component;

import com.paytech.acquiring.domain.checkout.CheckoutUpstreamProtocolException;
import com.paytech.acquiring.integration.counterparty.CounterpartyCustomerClient;
import com.paytech.counterparty.api.customer.CounterpartyCustomerApi;
import com.paytech.counterparty.api.customer.request.ResolveCustomerRequestBody;
import com.paytech.counterparty.api.enums.CardSavingIneligibleReason;
import com.paytech.counterparty.api.enums.CounterpartyErrorCode;
import com.paytech.foundation.common.error.FailException;

import lombok.AllArgsConstructor;

/** 只适配 counterparty 内部 DTO；HTTP、MDC 和错误解码交给公共客户端。 */
@Component
@AllArgsConstructor
public class CounterpartyCustomerClientImpl implements CounterpartyCustomerClient {
    private static final String DECIMAL_ID = "[1-9][0-9]{0,18}";

    private final CounterpartyCustomerApi customers;

    /** 响应必须给出合法主键与编号。 */
    @Override
    public long resolveCustomerId(long clientId, String consumerReference) {
        var body = customers.resolveCustomer(new ResolveCustomerRequestBody(Long.toString(clientId), consumerReference));
        if (body == null || body.getCustomerNo() == null) {
            throw new CheckoutUpstreamProtocolException("counterparty 建或取 Customer 响应无效");
        }
        return parseId(body.getCustomerId());
    }

    /** counterparty 返回 CUSTOMER_NOT_FOUND 时视为不存在，其余错误原样抛出。 */
    @Override
    public String customerNo(long customerId) {
        try {
            var body = customers.getCustomer(Long.toString(customerId));
            if (body == null || body.getCustomerNo() == null || !Long.toString(customerId).equals(body.getCustomerId())) {
                throw new CheckoutUpstreamProtocolException("counterparty Customer 响应无效");
            }
            return body.getCustomerNo();
        } catch (FailException failure) {
            if (CounterpartyErrorCode.CUSTOMER_NOT_FOUND.getCode().equals(failure.getCode())) {
                return null;
            }
            throw failure;
        }
    }

    /** 可用时不得带原因；不可用时原因必须是已登记代码。 */
    @Override
    public CardSavingDecision cardSavingEligibility(long clientId, long customerId) {
        var body = customers.cardSavingEligibility(Long.toString(clientId), Long.toString(customerId));
        if (body == null || body.isEligible() == (body.getReason() != null)
            || (body.isEligible() && body.getCustomerNo() == null)
            || (body.getReason() != null && Arrays.stream(CardSavingIneligibleReason.values())
                .noneMatch(reason -> reason.getCode().equals(body.getReason())))) {
            throw new CheckoutUpstreamProtocolException("counterparty 存卡资格响应无效");
        }
        return new CardSavingDecision(body.isEligible(), body.getReason(), body.getCustomerNo());
    }

    /** 主键必须是规范正十进制 BIGINT。 */
    static long parseId(String value) {
        if (value == null || !value.matches(DECIMAL_ID)) {
            throw new CheckoutUpstreamProtocolException("counterparty Customer 主键无效");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException outOfRange) {
            throw new CheckoutUpstreamProtocolException("counterparty Customer 主键超出范围", outOfRange);
        }
    }
}
