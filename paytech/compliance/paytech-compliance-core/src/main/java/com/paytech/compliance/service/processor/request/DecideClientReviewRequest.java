package com.paytech.risk.compliance.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.paytech.risk.compliance.domain.ClientReviewAction;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** O07/O08/O09 审核结论；动作由操作路径确定。 */
@Getter
@AllArgsConstructor
public class DecideClientReviewRequest {
    /** 主任务 ID。 */
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
    /** 退回分区；仅退回必填。 */
    @Pattern(regexp = "^(BASIC|BUSINESS|PEOPLE|MATERIALS)$")
    private final String returnSection;
    /** 审核结论。 */
    @NotNull
    private final ClientReviewAction action;
}
