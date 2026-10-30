package com.paytech.payout.service.processor.request;

import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 对一个收款账户的状态命令：设默认、关闭、停用、恢复。 */
@Getter
@AllArgsConstructor
public class BeneficiaryAccountCommandRequest {
    /** 收款账户主键 */
    @NotNull
    private final Long beneficiaryAccountId;

    /** 归属校验的 Owner 类型；运营可为空 */
    private final BeneficiaryOwnerType ownerType;

    /** 归属校验的 Owner 标识；运营可为空 */
    private final Long ownerId;

    /** 原因代码；关闭、停用必填 */
    private final String reasonCode;

    /** 期望的账户版本 */
    @NotNull
    private final Long expectedVersion;

    /** 操作人类型 */
    @NotNull
    private final BeneficiaryActorType actorType;

    /** 操作人账号 */
    @NotNull
    private final Long actorId;

    /** 来源键（Request ID） */
    @NotBlank
    private final String sourceKey;
}
