package com.paytech.tax.api.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 一张税务单据 */
@Getter
@Setter
public class TaxDocumentResponseBody {
    /** 单据 ID */
    private String documentId;

    /** 单据类型（TaxDocumentKind） */
    private String documentKind;

    /** 开具方式（IssuanceMode） */
    private String issuanceMode;

    /** 开票主体 Party ID */
    private String issuerPartyId;

    /** 注册 ID */
    private String registrationId;

    /** 号码序列，例如 SA-RCPT-2026 */
    private String seriesCode;

    /** 单据号码 */
    private String documentNumber;

    /** 开具时间 */
    private Instant issuedAt;

    /** 币种 */
    private String currency;

    /** 模板代码 */
    private String templateCode;

    /** 开具状态（TaxDocumentIssueStatus） */
    private String issueStatus;

    /** 报送状态：本期 NOT_REQUIRED */
    private String submissionStatus;

    /**
     * 文件地址 /files/v1/{fileId}
     * <li>ISSUED 时非空；下载由 BFF 向 file 服务申请授权链接，不经 tax。</li>
     */
    private String documentFileAddress;

    /** 文件 SHA-256 小写十六进制；ISSUED 时非空 */
    private String documentDigest;
}
