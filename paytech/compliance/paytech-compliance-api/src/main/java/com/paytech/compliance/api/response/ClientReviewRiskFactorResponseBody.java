package com.paytech.compliance.api.response;

import lombok.Getter;
import lombok.Setter;

/** 一项风险因子。 */
@Getter
@Setter
public class ClientReviewRiskFactorResponseBody {

    /** 因子代码。 */
    private String factorCode;

    /** 因子取值。 */
    private String factorValue;

    /** 来源：REVIEWER（本期只有人工）。 */
    private String source;

    /** 记录人。 */
    private String recordedBy;

    /** 记录时间。 */
    private String recordedAt;
}
