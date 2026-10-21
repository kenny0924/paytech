package com.paytech.commercial.api.term.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建平台标准价目模板方案（owner_type=PLATFORM_TEMPLATE，DRAFT）
 * <li>模板按 OE 维护、可命名多套，不属于合同，不参与运行时取价；方案编号在条款类型内唯一，同号同内容重放返回原方案。</li>
 */
@Getter
@Setter
public class CreateCommercialTermTemplateRequestBody {
    /** 方案编号，同一条款类型内唯一，创建后不可修改 */
    @NotBlank(message = "planCode 必填")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}", message = "planCode 格式不正确")
    private String planCode;

    /** 模板显示名，如「标准价 · AE001」 */
    @NotBlank(message = "templateName 必填")
    @Size(max = 128, message = "templateName 最长 128 字符")
    private String templateName;

    /** 条款类型，只允许 Client 条款类型（如 CLIENT_PRICING、PAYOUT_FEE、PLATFORM_FEE） */
    @NotBlank(message = "termType 必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "termType 格式不正确")
    private String termType;

    /** 模板适用的准确 OE */
    @NotBlank(message = "entityNo 必填")
    @Pattern(regexp = "[A-Z]{2}[0-9]{3}", message = "entityNo 格式不正确")
    private String entityNo;
}
