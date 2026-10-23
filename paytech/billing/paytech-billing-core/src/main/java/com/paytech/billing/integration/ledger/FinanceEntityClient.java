package com.paytech.billing.integration.ledger;

/** 把法律 Party 映射为 ledger ACTIVE Finance Entity；必须在数据库事务外调用。 */
public interface FinanceEntityClient {

    /**
     * 按法律 Party 解析 ACTIVE Finance Entity
     *
     * @param legalPartyId Partner 合同的平台签约 Party（订单上下文 platformServicePartyId）
     * @return Finance Entity 主键
     * @throws com.paytech.foundation.common.error.ErrorException 不存在或已关闭、上游不可用或响应无效；事实停在重试
     */
    long resolveFinanceEntityId(long legalPartyId);
}
