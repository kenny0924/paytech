package com.paytech.risk.control.domain.limittier;

/**
 * 档位列表顶部统计
 *
 * @param verification VERIFICATION 数
 * @param observation OBSERVATION 数
 * @param standard STANDARD 数
 * @param restricted RESTRICTED 数
 * @param pendingApproval 待审批申请数
 * @param observationDue 观察期已过目标天数的数
 */
public record LimitTierStatistics(long verification, long observation, long standard, long restricted,
    long pendingApproval, long observationDue) { }
