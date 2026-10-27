package com.paytech.client.master.api.onboardingadmin.response;

import lombok.Getter;
import lombok.Setter;

/** CM-12 PayFac 入驻码。 */
@Getter
@Setter
public class OnboardingCodeResponseBody {
    /** 入驻码 ID。 */
    private String codeId;

    /** 入驻码明文 XXXX-XXXX；只在签发响应中返回，查询与作废结果为空。 */
    private String code;

    /** 入驻码掩码 ****-末 4 位，供列表展示。 */
    private String codeMasked;

    /** 签发 Local Partner ID。 */
    private String localPartnerId;

    /** 状态 ACTIVE、REVOKED、EXPIRED。 */
    private String status;

    /** 生效 UTC 时间。 */
    private String effectiveFrom;

    /** 失效 UTC 时间，可空。 */
    private String effectiveTo;

    /** 最大使用次数，可空。 */
    private Integer maxUses;

    /** 已使用次数。 */
    private int useCount;

    /** 签发说明，可空。 */
    private String note;

    /** 签发 Operations Account。 */
    private String issuedBy;

    /** 签发 UTC 时间。 */
    private String issuedAt;

    /** 作废人，可空。 */
    private String revokedBy;

    /** 作废 UTC 时间，可空。 */
    private String revokedAt;
}
