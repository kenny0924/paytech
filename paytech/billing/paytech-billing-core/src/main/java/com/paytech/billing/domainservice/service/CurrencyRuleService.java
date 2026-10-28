package com.paytech.billing.domainservice.service;

import com.paytech.billing.domain.CurrencyRule;

/** 币种规则读取的业务唯一入口：只读参考数据客户端的进程内币种快照，从不外呼，可在事务内调用。 */
public interface CurrencyRuleService {

    /**
     * 读取启用过（ACTIVE 或 INACTIVE）的币种规则
     * <li>停用只拦截新用途（Q2）：计费事实登记与已登记事实的精度均按启用过判断；DRAFT 从未生效，任何用途都拒绝。</li>
     *
     * @param currency ISO 4217 币种代码
     * @return 该币种规则
     * @throws com.paytech.foundation.common.error.BizCheckFailException 快照中没有该币种或币种为 DRAFT
     */
    CurrencyRule requireUsable(String currency);
}
