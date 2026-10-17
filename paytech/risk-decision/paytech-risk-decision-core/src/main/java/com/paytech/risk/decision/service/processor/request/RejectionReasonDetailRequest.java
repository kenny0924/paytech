package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按永久原因编码查询当前字典详情。 */
@Getter
@AllArgsConstructor
public class RejectionReasonDetailRequest {

    /**
     * 拒绝原因编码。
     * <li>必填，1–64 位，以大写字母开头，仅允许大写字母、数字和下划线。</li>
     */
    @NotNull(message = "原因编码必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "原因编码格式无效")
    private final String reasonCode;
}
