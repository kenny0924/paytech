package com.paytech.payout.service.processor.request;

import com.paytech.payout.api.enums.BeneficiaryRailType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 查询字段要求。 */
@Getter
@AllArgsConstructor
public class GetBeneficiaryRequirementRequest {
    /** 收款方式 */
    @NotNull
    private final BeneficiaryRailType railType;

    /** 开户国家 */
    @NotBlank
    private final String bankCountry;

    /** 币种 */
    @NotBlank
    private final String currency;
}
