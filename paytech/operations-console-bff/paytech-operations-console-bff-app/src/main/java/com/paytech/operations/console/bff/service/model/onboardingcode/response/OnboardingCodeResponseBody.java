package com.paytech.operations.console.bff.service.model.onboardingcode.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 入驻码页的一条入驻码：只给掩码
 * <li>client-master 列表与作废结果只给掩码（****-XXXX），明文只在签发响应中出现一次；BFF 校验并只透出掩码。</li>
 */
@Getter
@AllArgsConstructor
public class OnboardingCodeResponseBody {

    /** 入驻码 ID */
    private final String codeId;

    /** 签发的 Local Partner */
    private final String localPartnerId;

    /** 掩码，如 ****-7Q2K */
    private final String codeMasked;

    /** ACTIVE / REVOKED / EXPIRED */
    private final String status;

    /** 生效时间（UTC） */
    private final String effectiveFrom;

    /** 截止时间（UTC）；null 为不过期 */
    private final String effectiveTo;

    /** 最多使用次数；null 为不限 */
    private final Integer maxUses;

    /** 已使用次数 */
    private final int useCount;

    /** 内部备注 */
    private final String note;

    /** 签发人 Account ID */
    private final String issuedBy;

    /** 签发时间（UTC） */
    private final String issuedTime;

    /** 作废人 Account ID */
    private final String revokedBy;

    /** 作废时间（UTC） */
    private final String revokedTime;
}
