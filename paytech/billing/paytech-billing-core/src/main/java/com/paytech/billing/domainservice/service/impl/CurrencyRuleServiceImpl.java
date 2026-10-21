package com.paytech.billing.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.billing.core.common.CurrencySpecSnapshot;
import com.paytech.billing.domain.CurrencyRule;
import com.paytech.billing.domainservice.service.CurrencyRuleService;
import com.paytech.foundation.common.error.BizCheckFailException;

import lombok.AllArgsConstructor;

/** CurrencyRuleService 实现：只读 {@link CurrencySpecSnapshot}；查不到时只置待刷新标记，回源由刷新调度在事务外完成。 */
@Service
@AllArgsConstructor
public class CurrencyRuleServiceImpl implements CurrencyRuleService {
    private final CurrencySpecSnapshot currencySpecSnapshot;

    /** 快照中没有该币种时请求刷新并拒绝；DRAFT 拒绝。 */
    @Override
    public CurrencyRule requireUsable(String currency) {
        CurrencyRule rule = currencySpecSnapshot.find(currency);
        if (rule == null) {
            currencySpecSnapshot.requestRefresh();
            throw new BizCheckFailException("币种不存在或尚未同步: " + currency + ", 已请求刷新 Platform 币种快照，请稍后重试");
        }
        if (!rule.usable()) {
            throw new BizCheckFailException("币种未启用: " + currency + ", 状态 " + rule.getStatus().getCode());
        }
        return rule;
    }
}
