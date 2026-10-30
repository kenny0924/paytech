package com.paytech.tax.core.dal.dataobject;

import java.time.Instant;
import java.time.LocalDate;

import com.paytech.tax.api.enums.TaxReportingStatus;
import com.paytech.tax.core.enums.TaxTransactionKind;
import com.paytech.tax.core.enums.TaxTransactionOrigin;

import lombok.Getter;
import lombok.Setter;

/** t_tax_transaction：正式税务交易；只有底稿状态可更新。 */
@Getter
@Setter
public class TaxTransaction {
    /** Snowflake 主键 */
    private Long id;

    /** 计算 ID */
    private Long calculationId;

    /** 来源系统 */
    private String sourceSystem;

    /** 来源事实类型 */
    private String sourceFactType;

    /** 来源事实 ID */
    private String sourceFactId;

    /** 税务范围 */
    private String taxScopeKey;

    /** 来源方式 */
    private TaxTransactionOrigin origin;

    /** 交易类型 */
    private TaxTransactionKind transactionKind;

    /** 纳税主体 Party ID */
    private Long taxSubjectPartyId;

    /** 币种 */
    private String currency;

    /** 发生时间 */
    private Instant occurredAt;

    /** 法定税点日期 */
    private LocalDate taxPointDate;

    /** 税点依据 */
    private String taxPointBasis;

    /** 登记时间 */
    private Instant registeredAt;

    /** 底稿状态 */
    private TaxReportingStatus reportingStatus;

    /** 底稿阻断原因；READY 为空 */
    private String reportingBlockReason;

    /** 来源载荷 Hash */
    private String sourcePayloadHash;

    /** 创建时间 */
    private Instant createTime;

    /** 更新时间 */
    private Instant updateTime;
}
