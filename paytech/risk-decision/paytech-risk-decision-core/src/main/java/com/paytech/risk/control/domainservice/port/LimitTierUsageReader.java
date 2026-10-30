package com.paytech.risk.control.domainservice.port;

import java.time.Instant;
import java.util.List;

import com.paytech.risk.control.domain.limittier.CurrencyUsage;
import com.paytech.risk.control.domain.limittier.TierQuotaVariable;

/**
 * 档位读取决策模块事实的端口：放行累计事件与风控常量（实现位于 decision，依赖方向 decision → control 不变）
 * <li>只读本库，不发起外部调用；可在事务外或事务内调用。</li>
 */
public interface LimitTierUsageReader {
    /**
     * 统计 Client 在 [from, to) 内风控放行的支付尝试数（PAYMENT_ATTEMPT_ALLOWED，CLIENT 主体）
     *
     * @param clientId Client ID
     * @param from 起点，含
     * @param to 终点，不含
     * @return 放行次数
     */
    long countAllowedPaymentAttempts(long clientId, Instant from, Instant to);

    /**
     * 按币种合计 Client UTC 当日与近 30 个 UTC 自然日（含当日）风控放行的支付尝试金额（读日放行汇总，与日 / 月额度评估同口径）
     *
     * @param clientId Client ID
     * @param at 当前时间，决定 UTC 当日
     * @return 近 30 日有放行的币种，按币种代码排序
     */
    List<CurrencyUsage> allowedPaymentAmounts(long clientId, Instant at);

    /**
     * 读取编码以 TIER_ 开头的 ACTIVE 常量与集合变量
     *
     * @return 变量原值，按编码排序；集合成员按排序读取
     */
    List<TierQuotaVariable> tierQuotaVariables();
}
