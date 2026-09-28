package com.paytech.commerce.test.builder.customer;

import com.paytech.commerce.customer.api.v1.payment.request.SubmitPaymentRequestBody;

/** 仅使用公开测试卡值，不读取真实卡或生产信息。 */
public final class SubmitPaymentRequestBuilder {
    private SubmitPaymentRequestBuilder() {}

    public static SubmitPaymentRequestBody forContract(long contractId) {
        var legal = new SubmitPaymentRequestBody.LegalConfirmationInfo();
        legal.setConsumerContractId(Long.toString(contractId));
        legal.setConfirmed(true);
        var card = new SubmitPaymentRequestBody.CardInfo();
        card.setPan("4242424242424242");
        card.setExpiryMonth("12");
        card.setExpiryYear("2030");
        card.setCardholderName("TEST CONSUMER");
        card.setCvv("123");
        var body = new SubmitPaymentRequestBody();
        body.setLegalConfirmation(legal);
        body.setCard(card);
        return body;
    }
}
