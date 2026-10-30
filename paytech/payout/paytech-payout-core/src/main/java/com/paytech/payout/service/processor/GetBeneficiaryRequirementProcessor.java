package com.paytech.payout.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.payout.api.enums.PayoutErrorCode;
import com.paytech.payout.core.configuration.BeneficiaryRequirementProperties.RouteRequirement;
import com.paytech.payout.domainservice.validator.BeneficiaryFieldValidator;
import com.paytech.payout.service.processor.request.GetBeneficiaryRequirementRequest;

import lombok.AllArgsConstructor;

/** 查询收款方式 × 国家 × 币种的字段要求（P12）；与提交校验共用同一清单匹配规则，清单没有时 404。 */
@Component
@AllArgsConstructor
public class GetBeneficiaryRequirementProcessor extends AbstractProcessor<GetBeneficiaryRequirementRequest, RouteRequirement> {
    private final BeneficiaryFieldValidator beneficiaryFieldValidator;

    /** 匹配字段清单。 */
    @Override
    protected RouteRequirement processRequest(GetBeneficiaryRequirementRequest request) {
        RouteRequirement route = beneficiaryFieldValidator.findRoute(request.getRailType(), request.getBankCountry(),
            request.getCurrency());
        if (route == null) {
            throw new FailException(PayoutErrorCode.BENEFICIARY_REQUIREMENT_NOT_FOUND, "该国家 / 币种没有字段要求: "
                + request.getRailType().getCode() + " " + request.getBankCountry() + " " + request.getCurrency());
        }
        return route;
    }
}
