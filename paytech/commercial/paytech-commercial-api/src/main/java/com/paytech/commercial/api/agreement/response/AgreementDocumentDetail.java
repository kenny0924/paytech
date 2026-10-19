package com.paytech.commercial.api.agreement.response;

import lombok.Getter;
import lombok.Setter;

/** 合同文件引用。 */
@Getter
@Setter
public class AgreementDocumentDetail {
    /** 文件引用 ID */
    private String agreementDocumentId;

    /** 文件用途：SIGNING_DOCUMENT、ANNEX、EXECUTED_DOCUMENT、TERMINATION_NOTICE */
    private String documentType;

    /** Platform Support 文件 ID */
    private String fileId;

    /** 文件 SHA-256 */
    private String contentHash;
}
