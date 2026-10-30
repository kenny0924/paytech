package com.paytech.payout.service.processor.request;

import com.paytech.payout.api.enums.BeneficiaryRevealReason;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 运营查看收款账户原值命令。 */
@Getter
@AllArgsConstructor
public class RevealBeneficiaryAccountRequest {
    /** 收款账户主键 */
    @NotNull
    private final Long beneficiaryAccountId;

    /** 查看人账号 */
    @NotNull
    private final Long actorId;

    /** 查看原因 */
    @NotNull
    private final BeneficiaryRevealReason reason;

    /** 调用方是否为 Operations BFF */
    private final boolean operationsCaller;

    /** 来源键（Request ID） */
    @NotBlank
    private final String sourceKey;
}
