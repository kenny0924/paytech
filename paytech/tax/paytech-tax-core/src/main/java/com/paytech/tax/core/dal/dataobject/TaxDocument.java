package com.paytech.tax.core.dal.dataobject;

import java.time.Instant;

import com.paytech.tax.api.enums.IssuanceMode;
import com.paytech.tax.api.enums.TaxDocumentIssueStatus;
import com.paytech.tax.api.enums.TaxDocumentKind;
import com.paytech.tax.core.enums.DocumentSubmissionStatus;

import lombok.Getter;
import lombok.Setter;

/** t_tax_document：税务单据；号码 RESERVED 时占用，ISSUED 时写文件地址与摘要。 */
@Getter
@Setter
public class TaxDocument {
    /** Snowflake 主键 */
    private Long id;

    /** 交易 ID */
    private Long transactionId;

    /** 单据类型 */
    private TaxDocumentKind documentKind;

    /** 单据范围 */
    private String documentScopeKey;

    /** 开具方式 */
    private IssuanceMode issuanceMode;

    /** 开票主体 Party ID */
    private Long issuerPartyId;

    /** 注册 ID */
    private Long registrationId;

    /** 号码序列 */
    private String seriesCode;

    /** 单据号码 */
    private String documentNumber;

    /** 分号（票面日期）时间 */
    private Instant issuedAt;

    /** 币种 */
    private String currency;

    /** 地区包 ID */
    private Long policyId;

    /** 模板代码 */
    private String templateCode;

    /** 模板版本 */
    private Integer templateRevision;

    /** 内容结构版本 */
    private Integer contentSchemaVersion;

    /** 票面快照 JSON */
    private String documentContentJson;

    /** 开具状态 */
    private TaxDocumentIssueStatus issueStatus;

    /** 报送状态 */
    private DocumentSubmissionStatus submissionStatus;

    /** 报送来源；可空 */
    private String submissionSource;

    /** 文件地址 /files/v1/{fileId}；ISSUED 时非空 */
    private String documentFileAddress;

    /** 文件 SHA-256；ISSUED 时非空 */
    private String documentDigest;

    /** 作废原因；可空 */
    private String voidReason;

    /** 创建时间 */
    private Instant createTime;

    /** 更新时间 */
    private Instant updateTime;
}
