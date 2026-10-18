package com.paytech.risk.decision.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 名单命中变量的 Feature 参数；创建后不可修改。 */
public class ListFeatureParameters {

    /** 名单编码，名单须已存在。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String listCode;

    /**
     * 判断的主体
     * <li>必须与名单支持的主体类型一致。</li>
     */
    @NotBlank
    private String subjectType;

    /** 读取名单编码。 */
    public String getListCode() { return listCode; }

    /** 设置名单编码。 */
    public void setListCode(String listCode) { this.listCode = listCode; }

    /** 读取判断的主体。 */
    public String getSubjectType() { return subjectType; }

    /** 设置判断的主体。 */
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
}
