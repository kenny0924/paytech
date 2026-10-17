package com.paytech.risk.api.compliance.response;

/** Risk 已接收的分类。 */
public class ClientReviewClassificationResponseBody {

    /** 行业代码。 */
    private String industryCode;

    /** 四位 MCC。 */
    private String mccCode;

    /** 是否主要分类。 */
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

    /** 读取是否主要分类。 */
    public Boolean getPrimary() {
        return primary;
    }

    /** 设置是否主要分类。 */
    public void setPrimary(Boolean primary) {
        this.primary = primary;
    }
}
