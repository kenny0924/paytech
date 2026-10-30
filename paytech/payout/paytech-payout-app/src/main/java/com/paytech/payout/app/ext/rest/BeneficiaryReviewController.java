package com.paytech.payout.app.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.payout.api.beneficiary.BeneficiaryReviewApi;
import com.paytech.payout.api.beneficiary.response.BeneficiaryAccountResponseBody;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.api.enums.BeneficiaryReviewStage;
import com.paytech.payout.app.ext.rest.converter.BeneficiaryAccountConverter;
import com.paytech.payout.app.ext.rest.converter.PayoutWire;
import com.paytech.payout.service.processor.ListBeneficiaryReviewsProcessor;
import com.paytech.payout.service.processor.request.ListBeneficiaryReviewsRequest;

import lombok.AllArgsConstructor;

/** 核验队列的 HTTP 协议适配。 */
@RestController
@AllArgsConstructor
public class BeneficiaryReviewController implements BeneficiaryReviewApi {
    private final BeneficiaryAccountConverter converter;
    private final ListBeneficiaryReviewsProcessor listBeneficiaryReviewsProcessor;

    /** 分页查询核验队列。 */
    @Override
    public PagingResponseBody<BeneficiaryAccountResponseBody> listBeneficiaryReviews(String stage, String ownerType,
        String ownerId, Integer page, Integer size) {
        PayoutWire.requireOwnerPair(ownerType, ownerId);
        return listBeneficiaryReviewsProcessor.process(new ListBeneficiaryReviewsRequest(
            PayoutWire.parse(BeneficiaryReviewStage.class, stage), PayoutWire.parse(BeneficiaryOwnerType.class, ownerType),
            PayoutWire.id(ownerId), PagingRequestBody.of(page, size))).map(converter::toResponseBody);
    }
}
