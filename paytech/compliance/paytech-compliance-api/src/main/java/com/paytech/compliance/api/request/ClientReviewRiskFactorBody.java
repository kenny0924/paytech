package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 手工评级时记录的一项风险因子。 */
@Getter
@Setter
public class ClientReviewRiskFactorBody {

    /**
     * 因子代码
     * <li>COUNTRY_RISK、NATIONALITY_RISK、PEP、INDUSTRY_RISK、VOLUME、ENTITY_STRUCTURE、ADVERSE_MEDIA、PRIOR_REJECTION、MANUAL。</li>
     */
    @NotBlank
    @Pattern(regexp = "^(COUNTRY_RISK|NATIONALITY_RISK|PEP|INDUSTRY_RISK|VOLUME|ENTITY_STRUCTURE|ADVERSE_MEDIA|PRIOR_REJECTION|MANUAL)$")
    private String factorCode;

    /** 因子取值或依据，1—128 字符，例如 LOW、HIGH_RISK_JURISDICTION。 */
    @NotBlank
    @Size(max = 128)
    private String factorValue;
}
