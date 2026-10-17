package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 一条已确认的行业与 MCC 分类。 */
public class ClientReviewClassificationBody {

    /** 行业代码。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String industryCode;

    /** 四位 MCC。 */
    @NotBlank
    @Pattern(regexp = "^[0-9]{4}$")
    private String mccCode;

    /** 是否主要分类；集合中恰好一个。 */
    @NotNull
    private Boolean primary;

    /** 读取行业代码。 */
    public String getIndustryCode() {
        return industryCode;
    }

    /** 设置行业代码。 */
    public void setIndustryCode(String industryCode) {
        this.industryCode = industryCode;
    }

    /** 读取四位 MCC。 */
    public String getMccCode() {
        return mccCode;
    }

    /** 设置四位 MCC。 */
    public void setMccCode(String mccCode) {
        this.mccCode = mccCode;
    }

    /** 读取是否主要分类；集合中恰好一个。 */
    public Boolean getPrimary() {
        return primary;
    }

    /** 设置是否主要分类；集合中恰好一个。 */
    public void setPrimary(Boolean primary) {
        this.primary = primary;
    }
}
