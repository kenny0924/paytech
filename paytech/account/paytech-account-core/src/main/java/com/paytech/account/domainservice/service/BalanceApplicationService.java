package com.paytech.account.domainservice.service;

import com.paytech.account.core.enums.AppliedFactStatus;
import com.paytech.account.domain.FactRecord;

/**
 * 把一条经济事实应用到 Owner 余额（design/04 §5）
 * <li>必须在调用方已开启的本地事务内执行（EconomicFactRecorded 消费拦截器或重建事务），只读写 mor_account。</li>
 * <li>按 fact_id + fact_version 幂等：已处理的事实直接返回原状态。</li>
 * <li>版本替代：事实带 supersedesFactId 时，先冲回被替代事实已应用的余额变化，再应用本事实，两步同事务；被替代事实尚未到达时预登记，
 * 到达后不再应用。</li>
 * <li>映射不出合法余额变化时以业务拒绝失败，调用方事务整体回滚。</li>
 */
public interface BalanceApplicationService {

    /**
     * 应用一条经济事实
     *
     * @param fact 经济事实
     * @return 处理后的状态：APPLIED、SKIPPED，或重放时的原状态
     */
    AppliedFactStatus apply(FactRecord fact);
}
