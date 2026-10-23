package com.paytech.billing.domainservice.service;

import java.time.Instant;

import com.paytech.billing.domain.PriceSettlement;

/**
 * PAYMENT 事实到期转可用时间（balance_available_time）的 T+N 推算（技术方案 4.7，S8）
 * <li>只读 t_business_calendar / t_business_calendar_day，不开启事务、不调用外部依赖。</li>
 */
public interface SettlementTimeCalculator {

    /**
     * 推算到期转可用时间
     * <li>起算日 d0：业务时间转到结算时区的日期，日内秒数超过截止秒时为次日；工作日：例外日记录优先（WORKING 为工作日、NON_WORKING
     * 不是），无记录时周一至周五为工作日。</li>
     * <li>N &gt; 0：d0 之后第 N 个工作日（d0 不计）；N = 0：d0 为工作日取 d0，否则其后首个工作日。结果为该日结算时区 23:59:59 转 UTC。</li>
     *
     * @param businessTime 事实业务时间（UTC）
     * @param settlement Client 命中价格的结算四字段
     * @return 到期转可用时间（UTC）
     * @throws com.paytech.foundation.common.error.ErrorException 结算字段缺失或非法、日历不存在或未启用（配置缺口，事实停在重试，Q9）
     */
    Instant balanceAvailableTime(Instant businessTime, PriceSettlement settlement);
}
