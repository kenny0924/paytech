package com.paytech.payout.api.beneficiary;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.payout.api.beneficiary.response.BeneficiaryRequirementResponseBody;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 收款账户字段要求查询（technique 8.1 P12，调用方 Client BFF、Operations BFF；本期为内置静态清单）。 */
@HttpExchange(url = "/payout/v1/beneficiary-requirements", accept = "application/json")
public interface BeneficiaryRequirementApi {

    /**
     * 查询收款方式 × 开户国家 × 币种的字段要求
     *
     * @param railType 收款方式：LOCAL、SWIFT
     * @param bankCountry 开户国家
     * @param currency 币种
     * @return 字段要求；清单没有该组合时 404 BENEFICIARY_REQUIREMENT_NOT_FOUND
     */
    @GetExchange
    BeneficiaryRequirementResponseBody getBeneficiaryRequirement(
        @RequestParam("railType") @NotBlank @Pattern(regexp = "LOCAL|SWIFT") String railType,
        @RequestParam("bankCountry") @NotBlank @Pattern(regexp = "[A-Z]{2}") String bankCountry,
        @RequestParam("currency") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency);
}
