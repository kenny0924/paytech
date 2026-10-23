package com.paytech.billing.integration.reconciliation;

import com.paytech.billing.domain.ClearingAccount;

/** 解析 reconciliation 清算账户；必须在数据库事务外调用。 */
public interface ClearingAccountClient {

    /**
     * 按 MID + 币种解析 ACTIVE 清算账户
     *
     * @param midId 渠道商户号主键
     * @param currency 交易币种
     * @return 清算账户
     * @throws com.paytech.foundation.common.error.ErrorException 未归属或已停用、上游不可用或响应无效；事实停在重试（对账决定第 9 条）
     */
    ClearingAccount resolve(long midId, String currency);
}
