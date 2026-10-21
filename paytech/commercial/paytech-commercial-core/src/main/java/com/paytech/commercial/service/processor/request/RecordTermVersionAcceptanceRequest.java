package com.paytech.commercial.service.processor.request;

import java.time.Instant;

import com.paytech.commercial.domain.enums.AgreementPartyAcceptanceMethod;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 登记价格版本客户签署的内部请求；acceptanceReference 是业务唯一身份。 */
@Getter
@AllArgsConstructor
public class RecordTermVersionAcceptanceRequest {
    /** 版本 ID */
    @Positive
    private final long versionId;

    /** 方案根期望版本 */
    @Positive
    private final long expectedChangeVersion;

    /** 登记人账号 ID */
    @Positive
    private final long operatorAccountId;

    /** 签署 Party */
    @Positive
    private final long partyId;

    /** 签署方式 */
    @NotNull
    private final AgreementPartyAcceptanceMethod acceptanceMethod;

    /** 签署成立时刻 */
    @NotNull
    private final Instant acceptedAt;

    /** 签署编号 */
    @NotBlank
    private final String acceptanceReference;

    /** 价格附件文件 ID */
    @Positive
    private final long fileId;

    /** 价格附件 SHA-256 */
    @NotBlank
    private final String contentHash;
}
