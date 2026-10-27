package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 组织与关联人员的一条关系。 */
@Getter
@Setter
public class ClientReviewRelationBody {

    /** 关系 ID。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String relationId;

    /** 关联人员 Party ID。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String relatedPartyId;

    /**
     * 关系类型
     * <li>client-master 关系类型目录代码，例如 UBO、OWNERSHIP、CONTROL、DIRECTOR、SIGNATORY；辖区可扩展。</li>
     */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String relationType;

    /** 持股比例十进制字符串，可空。 */
    @Size(max = 32)
    private String ownershipPercentage;

    /** UBO 认定依据代码（OWNERSHIP、CONTROL、SENIOR_MANAGING_OFFICIAL 等），可空。 */
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String uboBasis;

    /** 职务名称，可空。 */
    @Size(max = 128)
    private String jobTitle;

    /** 关系开始日期 YYYY-MM-DD。 */
    @NotBlank
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private String effectiveFrom;

    /** 关系结束日期 YYYY-MM-DD（不含当日），可空。 */
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private String effectiveTo;
}
