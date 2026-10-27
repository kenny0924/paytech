package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 退回时登记的一项需求级错误。 */
@Getter
@Setter
public class ClientReviewReturnErrorBody {

    /**
     * 需求键
     * <li>与 client-master 需求清单一致，例如 document.REGISTRATION、profile.organization.legal_name。</li>
     */
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_.\\-]{1,128}$")
    private String requirementKey;

    /** 需求所属主体 Party ID；与主体无关为空。 */
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String subjectPartyId;

    /** 错误码（设计 8.3 错误码族），例如 DOCUMENT_EXPIRED。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String code;

    /** 给 Client 的说明，可空，最多 1000 字符。 */
    @Size(max = 1000)
    private String reason;

    /** 补救动作码，例如 UPLOAD_DOCUMENT、PROVIDE_FIELD、ADD_PERSON。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String remediation;
}
