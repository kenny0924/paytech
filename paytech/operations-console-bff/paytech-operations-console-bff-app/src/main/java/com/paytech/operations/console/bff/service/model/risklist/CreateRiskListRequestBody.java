package com.paytech.operations.console.bff.service.model.risklist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 新建风控名单；创建后编码、主体类型与用途不可修改。
 */
@Getter
@Setter
public class CreateRiskListRequestBody {
    /**
     * 名单编码
     * <li>大写字母开头，1..64 位大写字母、数字或下划线。</li>
     */
    @NotBlank(message = "名单编码必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "名单编码格式无效")
    private String listCode;

    /**
     * 名单名称
     */
    @NotBlank(message = "名单名称必填")
    @Size(max = 255, message = "名单名称不超过 255 个字符")
    private String listName;

    /**
     * 主体类型
     * <li>CONSUMER、IP_ADDRESS、CLIENT、OPERATING_ENTITY、CARD_FINGERPRINT、CARD_BIN。</li>
     */
    @NotBlank(message = "主体类型必填")
    @Pattern(regexp = "CONSUMER|IP_ADDRESS|CLIENT|OPERATING_ENTITY|CARD_FINGERPRINT|CARD_BIN", message = "主体类型无效")
    private String subjectType;

    /**
     * 名单用途
     * <li>BLOCK 或 ALLOW，仅用于展示；命中后如何处置由规则决定。</li>
     */
    @NotBlank(message = "名单用途必填")
    @Pattern(regexp = "BLOCK|ALLOW", message = "名单用途只能为 BLOCK 或 ALLOW")
    private String purpose;
}
