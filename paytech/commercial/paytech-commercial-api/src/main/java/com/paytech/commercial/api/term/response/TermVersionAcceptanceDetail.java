package com.paytech.commercial.api.term.response;

import lombok.Getter;
import lombok.Setter;

/** 价格版本的客户签署记录。 */
@Getter
@Setter
public class TermVersionAcceptanceDetail {
    /** 签署记录 ID */
    private String versionAcceptanceId;

    /** 签署 Party ID */
    private String partyId;

    /** 签署方式：E_SIGNATURE、CLICK_ACCEPTANCE、MANUAL_SIGNATURE */
    private String acceptanceMethod;

    /** 签署成立时刻，UTC ISO 8601 */
    private String acceptedAt;

    /** 签署编号 */
    private String acceptanceReference;

    /** 价格附件文件 ID */
    private String fileId;

    /** 价格附件 SHA-256 */
    private String contentHash;

    /** 登记人 Account ID */
    private String recordedBy;
}
