package com.paytech.account.domainservice.service;

import com.paytech.account.domain.MovementContext;
import com.paytech.account.domain.model.BalanceMovementResult;

/**
 * Owner 账户余额桶移动命令（technique 2.1「链路与事务」第 3 行）
 * <li>一个本地短事务：按 (source_type, source_ref) 查已存在 → 幂等返回；否则锁入账流 → 锁转出 / 转入账户 → 检查状态与余额 →
 * 写移动、BALANCE_MOVEMENT 批次、两条流水与 AccountPosted 补偿事件。</li>
 * <li>不访问外部依赖；调用方在事务外完成输入校验。</li>
 */
public interface BalanceMovementService {

    /**
     * 执行或重放一次桶移动
     *
     * @param context 已校验的移动命令
     * @return 移动结果；同来源已存在时 replayed = true
     * @throws com.paytech.foundation.common.error.FailException 转出账户不存在（ACCOUNT_NOT_FOUND）、账户非 ACTIVE
     *         （ACCOUNT_NOT_ACTIVE）或转出桶余额不足（BALANCE_INSUFFICIENT）
     * @throws com.paytech.foundation.common.error.ConcurrentRequestException 同来源、同流或同账户并发写冲突
     */
    BalanceMovementResult move(MovementContext context);
}
