package com.paytech.tax.core.dal.dataobject;

import java.time.Instant;

import com.paytech.tax.api.enums.TaxEvidenceType;
import com.paytech.tax.core.enums.EvidenceValidationStatus;

import lombok.Getter;
import lombok.Setter;

/** t_tax_evidence：一次计算冻结的国家证据；追加写、不可变。 */
@Getter
@Setter
public class TaxEvidence {
    /** Snowflake 主键 */
    private Long id;

    /** 计算 ID */
    private Long calculationId;

    /** 证据类型 */
    private TaxEvidenceType evidenceType;

    /** 证据提供方系统 */
    private String sourceCode;

    /** 国家代码 */
    private String countryCode;

    /** 行政区代码；可空 */
    private String subdivisionCode;

    /** 核验状态 */
    private EvidenceValidationStatus validationStatus;

    /** 核验结果码；可空 */
    private String resultCode;

    /** 核验时间；可空 */
    private Instant checkedAt;

    /** 创建时间 */
    private Instant createTime;
}
