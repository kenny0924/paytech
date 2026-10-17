package com.paytech.risk.control.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.paytech.risk.control.core.enums.RiskListPurpose;
import com.paytech.risk.control.domain.list.RiskSubjectType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建名单的 Owner 输入。 */
@Getter
@AllArgsConstructor
public class CreateRiskListRequest {
    /** 操作人账号 ID。 */
    private final long actorAccountId;

    /** 名单编码，大写字母开头的大写字母数字下划线。 */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "名单编码格式无效")
    private final String listCode;

    /** 名单名称。 */
    @NotBlank
    @Size(max = 255)
    private final String listName;

    /** 名单支持的主体类型。 */
    @NotNull
    private final RiskSubjectType subjectType;

    /** 名单用途。 */
    @NotNull
    private final RiskListPurpose purpose;
}
