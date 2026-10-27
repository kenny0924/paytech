package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 受审主体的一项扩展属性（辖区属性定义中非 Vault 级的值）。 */
@Getter
@Setter
public class ClientReviewAttributeBody {

    /** 属性所属 Party ID。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String subjectPartyId;

    /** 属性定义代码。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String attributeCode;

    /** 属性值文本。 */
    @NotBlank
    @Size(max = 512)
    private String attributeValue;
}
