package com.paytech.risk.control.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 人工失效名单成员的 Owner 输入。 */
@Getter
@AllArgsConstructor
public class DeactivateRiskListEntryRequest {
    /** 操作人账号 ID。 */
    private final long actorAccountId;

    /** 名单编码。 */
    @NotBlank
    private final String listCode;

    /** 成员 ID。 */
    private final long entryId;

    /** 失效原因。 */
    @NotBlank
    @Size(max = 500)
    private final String reason;
}
