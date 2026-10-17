package com.paytech.risk.decision.api.control.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 创建风险名单的请求。 */
public class CreateRiskListRequestBody {

    /**
     * 名单编码
     * <li>大写字母开头的大写字母、数字或下划线，最长 64 位；全库唯一且不可修改。</li>
     */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String listCode;

    /** 名单名称，最长 255 字符。 */
    @NotBlank
    @Size(max = 255)
    private String listName;

    /**
     * 主体类型
     * <li>CONSUMER、IP_ADDRESS、CLIENT、OPERATING_ENTITY、CARD_FINGERPRINT 或 CARD_BIN；创建后不可修改。</li>
     */
    @NotBlank
    @Pattern(regexp = "^(CONSUMER|IP_ADDRESS|CLIENT|OPERATING_ENTITY|CARD_FINGERPRINT|CARD_BIN)$")
    private String subjectType;

    /**
     * 名单用途
     * <li>BLOCK 或 ALLOW，仅用于展示；命中后如何处置由规则决定。</li>
     */
    @NotBlank
    @Pattern(regexp = "^(BLOCK|ALLOW)$")
    private String purpose;

    /** 读取名单编码。 */
    public String getListCode() { return listCode; }

    /** 设置名单编码。 */
    public void setListCode(String listCode) { this.listCode = listCode; }

    /** 读取名单名称。 */
    public String getListName() { return listName; }

    /** 设置名单名称。 */
    public void setListName(String listName) { this.listName = listName; }

    /** 读取主体类型。 */
    public String getSubjectType() { return subjectType; }

    /** 设置主体类型。 */
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }

    /** 读取名单用途。 */
    public String getPurpose() { return purpose; }

    /** 设置名单用途。 */
    public void setPurpose(String purpose) { this.purpose = purpose; }
}
