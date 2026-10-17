package com.paytech.compliance.service.processor.request;

import com.paytech.compliance.core.enums.MaterialQualificationType;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按资格类型读取通用材料目录的 Owner 输入。 */
@Getter
@AllArgsConstructor
public class MaterialRequirementsRequest {
    /** 已由入口解析的资格类型。 */
    @NotNull
    private final MaterialQualificationType qualificationType;
}
