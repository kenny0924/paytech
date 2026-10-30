package com.paytech.tax.core.dal.dataobject;

import java.time.Instant;
import java.time.LocalDate;

import com.paytech.tax.core.enums.TaxPolicyStatus;

import lombok.Getter;
import lombok.Setter;

/** t_tax_policy：地区包版本；APPROVED 后内容不可修改。 */
@Getter
@Setter
public class TaxPolicy {
    /** Snowflake 主键 */
    private Long id;

    /** 包代码 */
    private String packageCode;

    /** 版本号 */
    private Integer revision;

    /** 辖区国家代码 */
    private String jurisdictionCode;

    /** 税种 */
    private String taxType;

    /** 制度代码 */
    private String schemeCode;

    /** 生效日（含） */
    private LocalDate effectiveFrom;

    /** 失效日（不含）；可空 */
    private LocalDate effectiveTo;

    /** policy_json 结构版本 */
    private Integer schemaVersion;

    /** 状态 */
    private TaxPolicyStatus status;

    /** 固定 Schema 的规则 JSON */
    private String policyJson;

    /** 官方依据列表 JSON */
    private String sourceRefsJson;

    /** 批准人；可空 */
    private String approvedBy;

    /** 批准时间；可空 */
    private Instant approvedAt;

    /** 创建时间 */
    private Instant createTime;

    /** 更新时间 */
    private Instant updateTime;
}
