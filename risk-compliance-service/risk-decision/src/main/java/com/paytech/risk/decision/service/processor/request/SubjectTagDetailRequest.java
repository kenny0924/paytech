package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按永久标签编码查询当前字典详情。 */
@Getter
@AllArgsConstructor
public class SubjectTagDetailRequest {

    /**
     * 拒绝标签编码。
     * <li>必填，1–64 位，以大写字母开头，仅允许大写字母、数字和下划线。</li>
     */
    @NotNull(message = "标签编码必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "标签编码格式无效")
    private final String tagCode;
}
