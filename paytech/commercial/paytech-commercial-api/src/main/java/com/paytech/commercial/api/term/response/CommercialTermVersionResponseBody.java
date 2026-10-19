package com.paytech.commercial.api.term.response;

import lombok.Getter;
import lombok.Setter;

/** 商业条款版本概要；价格集合经 GET /commercial-term-versions/v1/{versionId}/prices 读取。 */
@Getter
@Setter
public class CommercialTermVersionResponseBody {
    /** 版本 ID */
    private String versionId;

    /** 方案 ID */
    private String planId;

    /** 条款类型 */
    private String termType;

    /** 版本冻结的合同 ID */
    private String agreementId;

    /** 方案内版本号 */
    private String versionNo;

    /** 状态：DRAFT、PENDING_APPROVAL、APPROVED、SCHEDULED、ACTIVE、SUPERSEDED、EXPIRED */
    private String status;

    /**
     * 生效起点（UTC RFC 3339）
     * <li>DRAFT 至 APPROVED 期间为创建时的占位时刻，排期时写入真实起点。</li>
     */
    private String effectiveFrom;

    /** 生效终点（不含），被替代时写入；为空表示未预定结束 */
    private String effectiveTo;

    /** 舍入方式 */
    private String roundingMode;

    /** 定价模型 */
    private String pricingModel;

    /** 费用含税口径 */
    private String feeTaxTreatment;

    /** 方案 BCR 根当前修订版本，下一次写入的 expectedChangeVersion */
    private String changeVersion;
}
