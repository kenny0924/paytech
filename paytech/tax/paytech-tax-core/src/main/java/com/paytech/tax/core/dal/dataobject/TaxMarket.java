package com.paytech.tax.core.dal.dataobject;

import java.time.Instant;
import java.time.LocalDate;

import com.paytech.tax.api.enums.ManagedBy;
import com.paytech.tax.core.enums.MarketCollectionStatus;
import com.paytech.tax.core.enums.MarketObligation;

import lombok.Getter;
import lombok.Setter;

/** t_tax_market：纳税主体在一地区的准入评估与批准；按评估编号 + 修订版本化。 */
@Getter
@Setter
public class TaxMarket {
    /** Snowflake 主键 */
    private Long id;

    /** 纳税主体 Party ID */
    private Long subjectPartyId;

    /** 辖区国家代码 */
    private String jurisdictionCode;

    /** 税种 */
    private String taxType;

    /** 制度代码 */
    private String schemeCode;

    /** 评估编号 */
    private String assessmentNo;

    /** 修订号 */
    private Integer revision;

    /** 适用开始日（含） */
    private LocalDate validFrom;

    /** 适用结束日（不含）；可空 */
    private LocalDate validTo;

    /** 评估结论 */
    private MarketObligation obligation;

    /** 收税状态 */
    private MarketCollectionStatus collectionStatus;

    /** 操作方 */
    private ManagedBy managedBy;

    /** 注册 ID；可空 */
    private Long registrationId;

    /** 地区包 ID；可空 */
    private Long policyId;

    /** 批准人；可空 */
    private String approvedBy;

    /** 批准时间；未批准为空 */
    private Instant approvedAt;

    /** 复核到期日；可空 */
    private LocalDate reviewDueAt;

    /** 创建时间 */
    private Instant createTime;

    /** 更新时间 */
    private Instant updateTime;
}
