package com.paytech.commercial.domainservice.service;

import com.paytech.commercial.domain.RateCardQuery;
import com.paytech.commercial.domain.RateCardView;

/**
 * Client 费率卡查询（technique.md §2.7）：ACTIVE 且在时点生效的 CLIENT 合同、订阅产品、开通项（约束与保证金）、当前与已排期版本的
 * 价格（开通项 × 币种分块、按具体度排序）
 * <li>不返回 Partner 分润、Partner 买入价与平台服务费；只读本库，不开显式事务。</li>
 */
public interface RateCardQueryService {
    /**
     * 读取费率卡
     *
     * @param query Client、时点与可选币种
     * @return 费率卡
     */
    RateCardView load(RateCardQuery query);
}
