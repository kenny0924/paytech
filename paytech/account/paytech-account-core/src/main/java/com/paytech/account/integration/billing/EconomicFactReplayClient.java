package com.paytech.account.integration.billing;

import java.util.List;

import com.paytech.account.domain.BookIdentity;
import com.paytech.account.domain.FactRecord;

/** 经 billing-api 分页读取账簿主体的全部已批准经济事实（重建使用）；必须在数据库事务外调用，失败时原样抛出。 */
public interface EconomicFactReplayClient {

    /**
     * 读取账簿主体的全部已批准（含已被替代）经济事实
     *
     * @param book 账簿主体身份
     * @return 按 factId 升序的事实
     */
    List<FactRecord> listFacts(BookIdentity book);
}
