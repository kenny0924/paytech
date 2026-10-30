package com.paytech.payout.app.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.payout.api.beneficiary.BeneficiaryRequirementApi;
import com.paytech.payout.api.beneficiary.response.BeneficiaryRequirementResponseBody;
import com.paytech.payout.api.enums.BeneficiaryRailType;
import com.paytech.payout.app.ext.rest.converter.PayoutQueryConverter;
import com.paytech.payout.app.ext.rest.converter.PayoutWire;
import com.paytech.payout.service.processor.GetBeneficiaryRequirementProcessor;
import com.paytech.payout.service.processor.request.GetBeneficiaryRequirementRequest;

import lombok.AllArgsConstructor;

/** 字段要求查询的 HTTP 协议适配。 */
@RestController
@AllArgsConstructor
public class BeneficiaryRequirementController implements BeneficiaryRequirementApi {
    private final PayoutQueryConverter converter;
    private final GetBeneficiaryRequirementProcessor getBeneficiaryRequirementProcessor;

    /** 查询字段要求。 */
    @Override
    public BeneficiaryRequirementResponseBody getBeneficiaryRequirement(String railType, String bankCountry,
        String currency) {
        return converter.toRequirementBody(railType, bankCountry, currency, getBeneficiaryRequirementProcessor.process(
            new GetBeneficiaryRequirementRequest(PayoutWire.parse(BeneficiaryRailType.class, railType), bankCountry,
                currency)));
    }
}
