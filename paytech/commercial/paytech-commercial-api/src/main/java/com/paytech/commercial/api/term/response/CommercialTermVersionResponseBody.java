package com.paytech.commercial.api.term.response;

import java.util.List;

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

    /** 状态：DRAFT、PENDING_APPROVAL、APPROVED、CLIENT_SIGNED、SCHEDULED、ACTIVE、SUPERSEDED、EXPIRED */
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

    /** 方案 Owner 类型：CLIENT、LOCAL_PARTNER、PLATFORM_TEMPLATE */
    private String ownerType;

    /** 基于模板生成时的来源模板版本 ID，没有为空 */
    private String sourceTemplateVersionId;

    /** 存量版本免签（迁移前已排期 / 生效的版本） */
    private Boolean signatureExempt;

    /** 破标组件数（与来源模板比较为 BELOW_STANDARD 的组件条数） */
    private Integer belowStandardCount;

    /** 来源模板方案已有更新生效版本时的提示，没有为 null */
    private TemplateUpdateDetail templateUpdate;

    /** 客户签署记录，没有为 null */
    private TermVersionAcceptanceDetail acceptance;

    /**
     * 与合同开通项不相关的价格（按当前开通状态计算）
     * <li>只有 Client 合同方案计算，其他为空列表；保存价格时随响应返回，排期与激活时同一规则拦截。</li>
     */
    private List<PriceRelevanceWarning> relevanceWarnings;
}
