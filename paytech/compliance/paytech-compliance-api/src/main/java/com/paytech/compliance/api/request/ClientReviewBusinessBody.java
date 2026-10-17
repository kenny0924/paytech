package com.paytech.risk.api.compliance.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 提交时的经营资料；金额为十进制字符串，不含行业或 MCC。 */
public class ClientReviewBusinessBody {

    /** 经营模式代码。 */
    @NotBlank
    @Size(max = 64)
    private String businessModelCode;

    /** 网站地址，可空。 */
    @Size(max = 2048)
    private String websiteUrl;

    /** 业务说明。 */
    @NotBlank
    @Size(max = 2000)
    private String businessDescription;

    /** 预计月交易额，十进制字符串。 */
    @NotBlank
    @Size(max = 25)
    private String expectedVolume;

    /** 预计客单价，十进制字符串，与月交易额同币种。 */
    @NotBlank
    @Size(max = 25)
    private String expectedTicket;

    /** 交易币种，ISO 4217。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z]{3}$")
    private String currency;

    /** 读取经营模式代码。 */
    public String getBusinessModelCode() {
        return businessModelCode;
    }

    /** 设置经营模式代码。 */
    public void setBusinessModelCode(String businessModelCode) {
        this.businessModelCode = businessModelCode;
    }

    /** 读取网站地址，可空。 */
    public String getWebsiteUrl() {
        return websiteUrl;
    }

    /** 设置网站地址，可空。 */
    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    /** 读取业务说明。 */
    public String getBusinessDescription() {
        return businessDescription;
    }

    /** 设置业务说明。 */
    public void setBusinessDescription(String businessDescription) {
        this.businessDescription = businessDescription;
    }

    /** 读取预计月交易额，十进制字符串。 */
    public String getExpectedVolume() {
        return expectedVolume;
    }

    /** 设置预计月交易额，十进制字符串。 */
    public void setExpectedVolume(String expectedVolume) {
        this.expectedVolume = expectedVolume;
    }

    /** 读取预计客单价，十进制字符串，与月交易额同币种。 */
    public String getExpectedTicket() {
        return expectedTicket;
    }

    /** 设置预计客单价，十进制字符串，与月交易额同币种。 */
    public void setExpectedTicket(String expectedTicket) {
        this.expectedTicket = expectedTicket;
    }

    /** 读取交易币种，ISO 4217。 */
    public String getCurrency() {
        return currency;
    }

    /** 设置交易币种，ISO 4217。 */
    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
