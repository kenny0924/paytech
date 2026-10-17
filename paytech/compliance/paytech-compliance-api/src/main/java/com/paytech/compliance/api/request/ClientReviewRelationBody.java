package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 组织与关联人员的一条关系。 */
public class ClientReviewRelationBody {

    /** CCT 关系 ID。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String relationId;

    /** 关联人员 Party ID。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String relatedPartyId;

    /**
     * 关系类型
     * <li>OWNERSHIP、CONTROL、DIRECTOR、SIGNATORY 或 UBO。</li>
     */
    @NotBlank
    @Pattern(regexp = "^(OWNERSHIP|CONTROL|DIRECTOR|SIGNATORY|UBO)$")
    private String relationType;

    /** 持股比例十进制字符串，可空。 */
    @Size(max = 32)
    private String ownershipPercentage;

    /** 关系开始日期 YYYY-MM-DD。 */
    @NotBlank
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private String effectiveFrom;

    /** 关系结束日期 YYYY-MM-DD（不含当日），可空。 */
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private String effectiveTo;

    /** 读取CCT 关系 ID。 */
    public String getRelationId() {
        return relationId;
    }

    /** 设置CCT 关系 ID。 */
    public void setRelationId(String relationId) {
        this.relationId = relationId;
    }

    /** 读取关联人员 Party ID。 */
    public String getRelatedPartyId() {
        return relatedPartyId;
    }

    /** 设置关联人员 Party ID。 */
    public void setRelatedPartyId(String relatedPartyId) {
        this.relatedPartyId = relatedPartyId;
    }

    /** 读取关系类型。 */
    public String getRelationType() {
        return relationType;
    }

    /** 设置关系类型。 */
    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    /** 读取持股比例十进制字符串，可空。 */
    public String getOwnershipPercentage() {
        return ownershipPercentage;
    }

    /** 设置持股比例十进制字符串，可空。 */
    public void setOwnershipPercentage(String ownershipPercentage) {
        this.ownershipPercentage = ownershipPercentage;
    }

    /** 读取关系开始日期 YYYY-MM-DD。 */
    public String getEffectiveFrom() {
        return effectiveFrom;
    }

    /** 设置关系开始日期 YYYY-MM-DD。 */
    public void setEffectiveFrom(String effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    /** 读取关系结束日期 YYYY-MM-DD（不含当日），可空。 */
    public String getEffectiveTo() {
        return effectiveTo;
    }

    /** 设置关系结束日期 YYYY-MM-DD（不含当日），可空。 */
    public void setEffectiveTo(String effectiveTo) {
        this.effectiveTo = effectiveTo;
    }
}
