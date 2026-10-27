package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Client 提交前完成的一项声明。 */
@Getter
@Setter
public class ClientReviewDeclarationBody {

    /** 声明类型代码，例如 OWNERSHIP_COMPLETE、PEP_DECLARATION、INFORMATION_TRUE。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String declarationType;

    /** 声明值，例如 TRUE、FALSE 或条款版本对应的选择。 */
    @NotBlank
    @Size(max = 64)
    private String declaredValue;

    /** 声明时间，UTC ISO-8601。 */
    @NotBlank
    @Size(max = 32)
    private String declaredAt;
}
