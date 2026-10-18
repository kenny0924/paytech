package com.paytech.ledger.integration.billing;

import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.domain.EconomicFactProgress;

/** 读取 billing 对账簿主体的经济事实发布进度；必须在数据库事务外调用。 */
public interface EconomicFactProgressClient {

    /**
     * 查询账簿主体的发布进度
     *
     * @param book ledger 账簿（主体类型、主体标识与币种即 billing 的账簿主体参数）
     * @return 未发布计数与已发布最大 factId
     * @throws RuntimeException billing 不可用、拒绝或响应不合法；调用方按失败关闭处理
     */
    EconomicFactProgress getProgress(LedgerBook book);
}
