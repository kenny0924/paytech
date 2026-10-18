package com.paytech.operations.console.bff.service.model.risklist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 失效名单成员；只追加状态，不删除。
 */
@Getter
@Setter
public class DeactivateRiskListEntryRequestBody {
    /**
     * 失效原因
     */
    @NotBlank(message = "失效原因必填")
    @Size(max = 500, message = "失效原因不超过 500 个字符")
    private String reason;
}
