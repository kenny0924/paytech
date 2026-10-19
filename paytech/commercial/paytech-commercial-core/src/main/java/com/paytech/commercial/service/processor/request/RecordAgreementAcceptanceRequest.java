package com.paytech.commercial.service.processor.request;

import java.time.Instant;

import com.paytech.commercial.domain.enums.AgreementPartyAcceptanceMethod;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 登记一个 Party 签署结果的内部请求；acceptanceReference 是业务唯一身份。 */
@Getter
@Setter
public class RecordAgreementAcceptanceRequest {
    /** 操作者 Account ID */
    @NotNull
    @Positive
    private Long actorId;

    /** 合同 ID */
    @NotNull
    @Positive
    private Long agreementId;

    /** 法律 Party ID */
    @NotNull
    @Positive
    private Long partyId;

    /** 调用方期望的合同 BCR Root 版本 */
    @NotNull
    @Positive
    private Long expectedChangeVersion;

    /** 合同接受事件编号 */
    @NotBlank
    private String acceptanceReference;

    /** 签署方式 */
    @NotNull
    private AgreementPartyAcceptanceMethod acceptanceMethod;

    /** 实际接受的外部 Actor，可空 */
    private Long acceptedByActorId;

    /** 接受时刻 */
    @NotNull
    private Instant acceptedAt;

    /** 最终执行文件 ID，可空 */
    private Long executedFileId;

    /** 最终执行文件 SHA-256，可空，与 executedFileId 成组 */
    private String executedContentHash;
}
