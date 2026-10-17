package com.paytech.commerce.domain.customer;

import java.time.Instant;

/** 本次支付携带的法律事实；从三条接受与同 Session 证据导出，不作为请求幂等快照。 */
public record CheckoutAcceptanceReceipt(
        String submissionId,
        String orderId,
        String checkoutSessionId,
        String paymentIntentId,
        String consumerContractId,
        String consumerTermsAcceptanceId,
        String refundPolicyAcceptanceId,
        String privacyNoticeAcceptanceId,
        String legalAcceptanceNetworkEvidenceId,
        Instant acceptanceCompletedAt) {}
