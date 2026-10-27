package com.paytech.compliance.service.processor.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.paytech.compliance.domain.ClientReviewAction;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** O07/O08/O09 审核结论；动作由操作路径确定。 */
@Getter
@AllArgsConstructor
public class DecideClientReviewRequest {
    /** 主案件 ID。 */
    @Positive
    private final long caseId;
    /** 操作人 Account ID。 */
    @Positive
    private final long actorId;
    /** 原因代码。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private final String reasonCode;
    /** 内部审核理由；退回为空。 */
    @Size(max = 2000)
    private final String reason;
    /** 客户可见说明；退回必填。 */
    @Size(max = 1000)
    private final String publicNote;
    /** 兼容退回分区；可空。 */
    @Pattern(regexp = "^(BASIC|BUSINESS|PEOPLE|MATERIALS)$")
    private final String returnSection;
    /** 退回的需求级错误；仅退回必填，其他为空列表。 */
    @NotNull
    @Valid
    private final List<ReturnErrorItem> errors;
    /** 退回补件期限天数；仅退回必填。 */
    private final Integer dueDays;
    /** 审核结论。 */
    @NotNull
    private final ClientReviewAction action;
    /** 代操作的责任 PayFac；非代操作为空。 */
    private final Long onBehalfOfPartnerId;

    /** 一项退回需求级错误。 */
    @Getter
    @AllArgsConstructor
    public static class ReturnErrorItem {
        /** 需求键。 */
        @NotBlank
        private final String requirementKey;
        /** 主体 Party ID；可空。 */
        private final Long subjectPartyId;
        /** 错误码。 */
        @NotBlank
        private final String code;
        /** 给 Client 的说明。 */
        @Size(max = 1000)
        private final String reason;
        /** 补救动作码。 */
        @NotBlank
        private final String remediation;
    }
}
