package com.paytech.compliance.api.response;

import lombok.Getter;
import lombok.Setter;

/** 一次命中处置。 */
@Getter
@Setter
public class ClientReviewHitResolutionResponseBody {

    /** 处置版本，从 1 起。 */
    private Integer resolutionVersion;

    /** 处置结论。 */
    private String resolution;

    /** 处置原因码。 */
    private String reasonCode;

    /** 内部备注。 */
    private String note;

    /** 处置人。 */
    private String reviewerId;

    /** 处置时间。 */
    private String resolvedAt;

    /** 代操作的责任 PayFac；非代操作为空。 */
    private String onBehalfOfPartnerId;
}
