package com.paytech.account.domainservice.service;

import java.time.Instant;
import java.util.List;

import com.paytech.account.core.dal.dataobject.BalanceAppliedFact;

/**
 * 应付余额到期转可用（technique 2.1「链路与事务」第 4 行，设计 8.5）
 * <li>到期任务分页读取 maturity_status = PENDING 且到期时间已到的事实；每条事实一个本地短事务。</li>
 * <li>对事实流水中记入 *_PAYABLE / PENDING 的每个账户，按该账户在此事实上的净贷方金额（扣除替代冲回）做一次 FACT_MATURITY 移动
 * （source_ref = {factId}:{factVersion}:{accountId}），全部完成后事实标记 MATURED；重复执行按 source_ref 幂等。</li>
 */
public interface BalanceMaturityService {

    /**
     * 读取到期待移动的事实
     *
     * @param asOf 到期判断时间
     * @param afterId 主键游标，首页传 0
     * @param limit 每页条数
     * @return 按主键升序的事实
     */
    List<BalanceAppliedFact> findDue(Instant asOf, long afterId, int limit);

    /**
     * 移动一条事实的到期余额并标记 MATURED
     *
     * @param factId 计费事实 ID
     * @return 本次新建的移动数；事实已不在 PENDING 时为 0
     */
    int mature(long factId);
}
