package com.paytech.commercial.service.processor.request;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 终止合同的内部请求。 */
@Getter
@Setter
public class TerminateAgreementRequest {
    /** 操作者 Account ID */
    @NotNull
    @Positive
    private Long actorId;

    /** 合同 ID */
    @NotNull
    @Positive
    private Long agreementId;

    /** 调用方期望的合同 BCR Root 版本 */
    @NotNull
    @Positive
    private Long expectedChangeVersion;

    /** 合同生效终点（不含） */
    @NotNull
    private Instant effectiveTo;

    /** 终止通知文件 ID */
    @NotNull
    @Positive
    private Long noticeFileId;

    /** 终止通知文件 SHA-256 */
    @NotBlank
    private String noticeContentHash;
}
