package com.paytech.client.master.api.onboarding.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** CM-6 Client 确认一项声明。 */
@Getter
@Setter
public class DeclarationRequestBody {
    /**
     * 声明类型
     * <li>必须是申请冻结 Schema 中的 declaration.<类型> 字段，如 INFORMATION_TRUE、PEP_DECLARATION。</li>
     */
    @NotBlank(message = "declarationType 必填")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$", message = "declarationType 格式不正确")
    private String declarationType;

    /**
     * 声明取值
     * <li>TRUE 表示确认；FALSE 只用于记录未确认，不满足提交要求。</li>
     */
    @NotBlank(message = "declaredValue 必填")
    @Pattern(regexp = "^(TRUE|FALSE)$", message = "declaredValue 只能为 TRUE 或 FALSE")
    private String declaredValue;

    /** 声明所针对的条款或告知文本版本，可空。 */
    @Size(max = 64, message = "termsVersion 不能超过 64 个字符")
    private String termsVersion;
}
