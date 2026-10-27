package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 提交时的经营资料；金额为十进制字符串，不含行业或 MCC。 */
@Getter
@Setter
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
}
