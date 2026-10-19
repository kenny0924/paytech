package com.paytech.account.domainservice.service;

import com.paytech.account.core.enums.AppliedFactStatus;
import com.paytech.account.domain.FactRecord;

/**
 * 把一条计费事实入账到明细账（technique 2.1「链路与事务」第 1 行）
 * <li>必须在调用方已开启的本地事务内执行（BillingFactRecorded 消费拦截器事务），只读写 mor_account。</li>
 * <li>按 fact_id + fact_version 幂等：已处理的事实直接返回原状态，不生成新批次。</li>
 * <li>每条到达的事实生成一个 ECONOMIC_FACT 批次（可为 0 流水），同一事务写流水、余额、处理记录与 AccountPosted 补偿事件。</li>
 * <li>版本替代：被替代事实已入账时，在本批次先写冲回行（reverses_entry_id 指向原流水）再写新版本行；被替代事实尚未到达时预登记，
 * 到达后只生成 0 流水的 SKIPPED 批次。</li>
 * <li>映射不出合法流水时以业务拒绝失败，调用方事务整体回滚。</li>
 */
public interface BalanceApplicationService {

    /**
     * 入账一条计费事实
     *
     * @param fact 计费事实
     * @return 处理后的状态：APPLIED、SKIPPED，或重放时的原状态
     */
    AppliedFactStatus apply(FactRecord fact);
}
