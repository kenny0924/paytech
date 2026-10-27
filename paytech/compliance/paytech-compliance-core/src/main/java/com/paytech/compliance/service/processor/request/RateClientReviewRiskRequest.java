package com.paytech.compliance.service.processor.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 手工客户风险评级。 */
@Getter
@AllArgsConstructor
public class RateClientReviewRiskRequest {
    /** 主案件 ID。 */
    @Positive
    private final long caseId;
    /** 操作人 Account ID。 */
    @Positive
    private final long actorId;
    /** LOW、MEDIUM 或 HIGH。 */
    @NotBlank
    @Pattern(regexp = "^(LOW|MEDIUM|HIGH)$")
    private final String riskRating;
    /** 依据因子。 */
    @NotEmpty
    @Valid
    private final List<Factor> factors;
    /** 评级说明。 */
    @NotBlank
    @Size(max = 1000)
    private final String note;
    /** 代操作的责任 PayFac；非代操作为空。 */
    private final Long onBehalfOfPartnerId;

    /** 一项因子。 */
    @Getter
    @AllArgsConstructor
    public static class Factor {
        /** 因子代码。 */
        @NotBlank
        private final String factorCode;
        /** 因子取值。 */
        @NotBlank
        @Size(max = 128)
        private final String factorValue;
    }
}
