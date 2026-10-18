package com.paytech.ledger.integration.account;

import com.paytech.ledger.domain.BookBalanceConsistency;

/** 读取 account 对账簿的余额消费进度；必须在数据库事务外调用。 */
public interface BalanceConsistencyClient {

    /**
     * 查询账簿的余额消费进度
     *
     * @param ledgerBookId ledger 账簿主键
     * @return 已处理最大 factId 与非零渠道 / 银行账户数
     * @throws RuntimeException account 不可用、拒绝或响应不合法；调用方按失败关闭处理
     */
    BookBalanceConsistency getConsistency(long ledgerBookId);
}
