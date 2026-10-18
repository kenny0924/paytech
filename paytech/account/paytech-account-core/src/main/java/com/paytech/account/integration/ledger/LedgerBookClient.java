package com.paytech.account.integration.ledger;

import java.util.List;

import com.paytech.account.domain.LedgerAccountTotal;
import com.paytech.account.domain.LedgerBookRef;

/** 经 ledger-api 读取账簿身份与按分录聚合的 Owner 账户余额；必须在数据库事务外调用，失败时原样抛出由调用方失败关闭。 */
public interface LedgerBookClient {

    /**
     * 按主键读取账簿
     *
     * @param ledgerBookId ledger 账簿主键
     * @return 账簿身份；账簿不存在时以业务拒绝失败
     */
    LedgerBookRef getBook(long ledgerBookId);

    /**
     * 按经营主体与币种查找账簿
     *
     * @param entityNo Operating Entity 编号
     * @param currency 币种
     * @return 唯一账簿；不存在时为空，存在多个时以系统错误失败
     */
    LedgerBookRef findOperatingEntityBook(String entityNo, String currency);

    /**
     * 账簿内 Owner 账户按全部分录聚合的余额
     *
     * @param book 账簿
     * @return ledger 侧余额
     */
    List<LedgerAccountTotal> accountTotals(LedgerBookRef book);
}
