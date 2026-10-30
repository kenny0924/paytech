package com.paytech.payout.service.processor.request;

import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.api.enums.BeneficiaryRailType;
import com.paytech.payout.domain.BeneficiaryAccountDetails;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 提交新收款账户命令
 * <li>含原值：不生成 toString、不使用 Builder，只在 Vault Protect 前使用。</li>
 */
@Getter
@AllArgsConstructor
public class SubmitBeneficiaryAccountRequest {
    /** Owner 类型 */
    @NotNull
    private final BeneficiaryOwnerType ownerType;

    /** Owner 标识 */
    @NotNull
    private final Long ownerId;

    /** 收款方式 */
    @NotNull
    private final BeneficiaryRailType railType;

    /** 开户国家 */
    @NotBlank
    private final String bankCountry;

    /** 币种 */
    @NotBlank
    private final String currency;

    /** 字段原值（未规范化） */
    @NotNull
    private final BeneficiaryAccountDetails details;

    /** 提交人类型 */
    @NotNull
    private final BeneficiaryActorType actorType;

    /** 提交人账号 */
    @NotNull
    private final Long actorId;

    /** 来源键（Request ID） */
    @NotBlank
    private final String sourceKey;
}
