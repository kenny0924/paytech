package com.paytech.commercial.domainservice.service;

import java.util.List;

import com.paytech.commercial.domain.CommercialPriceQuery;
import com.paytech.commercial.domain.CommercialPriceView;
import com.paytech.commercial.domain.PricingFacts;

/**
 * 价格命中与不可变价格读取（technique.md §2.4，stage3 §2.3 与新增 5）：按冻结版本的完整历史内容读取，不改用当前 ACTIVE 版本或画像
 * <li>只读本库，不开显式事务：APPROVED 之后的版本、价格、组件与档位不再修改。</li>
 */
public interface CommercialPriceResolutionService {
    /**
     * 在冻结版本内命中唯一价格
     * <li>父链（合同、条款类型、Owner、OE 覆盖）不符或版本不是 contextAsOfTime 的适用版本：409 PRICE_AMBIGUOUS。</li>
     * <li>命中失败：422 PRICING_FACTS_INCOMPLETE / PRICE_NOT_FOUND，409 PRICE_AMBIGUOUS。</li>
     *
     * @param query 冻结父链、商业时点与币种
     * @param facts 已解析的定价事实
     * @return 命中价格
     * @throws com.paytech.foundation.common.error.FailException 版本不存在 NOT_FOUND
     */
    CommercialPriceView resolve(CommercialPriceQuery query, PricingFacts facts);

    /**
     * 按价格 ID 回读同一不可变价格
     *
     * @param termPriceId 价格 ID
     * @return 价格
     * @throws com.paytech.foundation.common.error.FailException 不存在 NOT_FOUND
     */
    CommercialPriceView getPrice(long termPriceId);

    /**
     * 版本下某 OE 的价格全集
     *
     * @param termVersionId 版本 ID
     * @param entityNo 准确 OE
     * @param termType 条款类型，须等于方案条款类型
     * @param currency 币种，可空
     * @return 按币种、优先级排序的价格
     * @throws com.paytech.foundation.common.error.FailException 版本不存在或条款类型不符 NOT_FOUND
     */
    List<CommercialPriceView> listVersionPrices(long termVersionId, String entityNo, String termType, String currency);
}
