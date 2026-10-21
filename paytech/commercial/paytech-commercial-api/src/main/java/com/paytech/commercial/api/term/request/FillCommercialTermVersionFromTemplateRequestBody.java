package com.paytech.commercial.api.term.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 从标准价目模板版本补齐 DRAFT 合同版本：只追加当前版本缺失的（OE × 币种 × 画像）价格行。 */
@Getter
@Setter
public class FillCommercialTermVersionFromTemplateRequestBody {
    /** 方案 BCR 根当前修订版本（CAS 令牌）；不等于当前值时 409。 */
    @NotBlank(message = "expectedChangeVersion 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "expectedChangeVersion 格式不正确")
    private String expectedChangeVersion;

    /** 模板版本 ID；必须属于同条款类型的 PLATFORM_TEMPLATE 方案，且状态为 RESOLVABLE（SCHEDULED / ACTIVE / SUPERSEDED） */
    @NotBlank(message = "templateVersionId 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "templateVersionId 格式不正确")
    private String templateVersionId;
}
