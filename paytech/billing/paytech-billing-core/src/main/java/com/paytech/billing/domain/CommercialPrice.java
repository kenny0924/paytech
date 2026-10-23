package com.paytech.billing.domain;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * commercial 在冻结条款版本内命中的唯一价格（Owner 为 commercial，经 CommercialPriceApi 只读取得）
 * <li>只保留计算与证据需要的字段；组件按 componentNo 升序。</li>
 */
@Getter
@Builder
public class CommercialPrice {
    /** 条款类型代码（CommercialCodes.TermType） */
    private final String termType;

    /** 条款版本，明细行 source_rule_version_id */
    private final long termVersionId;

    /** 方案 ID，证据 */
    private final String planId;

    /** 价格 ID，明细行 source_price_id */
    private final long priceId;

    /** 定价画像代码，证据 */
    private final String pricingProfileCode;

    /** 计算组件，按 componentNo 升序 */
    private final List<CommercialPriceComponent> components;

    /**
     * 结算四字段
     * <li>只有 Client 条款返回；Partner 条款为空。</li>
     */
    private final PriceSettlement settlement;
}
