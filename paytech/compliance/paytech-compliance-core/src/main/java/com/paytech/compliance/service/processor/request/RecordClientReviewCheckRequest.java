package com.paytech.compliance.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 录入一项人工检查结论。 */
@Getter
@AllArgsConstructor
public class RecordClientReviewCheckRequest {
    /** 主案件 ID。 */
    @Positive
    private final long caseId;
    /** 检查 ID。 */
    @Positive
    private final long checkId;
    /** 操作人 Account ID。 */
    @Positive
    private final long actorId;
    /** PASS 或 FAIL。 */
    @NotBlank
    @Pattern(regexp = "^(PASS|FAIL)$")
    private final String outcome;
    /** 结论原因码。 */
    @NotBlank
    private final String reasonCode;
    /** 名单版本或查询编号。 */
    @Size(max = 128)
    private final String evidenceRef;
    /** 内部备注。 */
    @Size(max = 1000)
    private final String note;
    /** 代操作的责任 PayFac；非代操作为空。 */
    private final Long onBehalfOfPartnerId;
}
