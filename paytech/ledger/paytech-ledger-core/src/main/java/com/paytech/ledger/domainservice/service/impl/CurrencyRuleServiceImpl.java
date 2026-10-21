package com.paytech.ledger.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.ledger.domain.CurrencyRule;
import com.paytech.ledger.domainservice.service.CurrencyRuleService;
import com.paytech.ledger.integration.platform.CurrencySpecSnapshot;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

import lombok.AllArgsConstructor;

/** 从 Platform 币种规则快照读取并按用途校验状态（Q2：INACTIVE 只拦新用途）。 */
@Service
@AllArgsConstructor
public class CurrencyRuleServiceImpl implements CurrencyRuleService {
    private final CurrencySpecSnapshot currencySpecSnapshot;

    /** DRAFT 从未生效，任何用途都拒绝。 */
    @Override
    public CurrencyRule requireUsable(String currency) {
        CurrencyRule rule = requireKnown(currency);
        if (!rule.isUsable()) {
            throw new BizCheckFailException("币种尚未启用: " + currency + ", 状态 " + rule.getStatus().getCode());
        }
        return rule;
    }

    /** 只有 ACTIVE 允许新用途。 */
    @Override
    public CurrencyRule requireActive(String currency) {
        CurrencyRule rule = requireKnown(currency);
        if (rule.getStatus() != CurrencySpecStatus.ACTIVE) {
            throw new BizCheckFailException("币种未启用，不能用于新业务: " + currency + ", 状态 "
                + rule.getStatus().getCode());
        }
        return rule;
    }

    /**
     * 快照中没有该币种时请求刷新，并以可重试的系统异常失败
     * <li>这是快照同步滞后而非业务拒绝：AccountPosted 消费按失败重投（不进死信），下一个检查周期回源后自动恢复；追平命令返回 500、不推进进度，
     * 可再次发起。</li>
     */
    CurrencyRule requireKnown(String currency) {
        CurrencyRule rule = currencySpecSnapshot.find(currency);
        if (rule == null) {
            currencySpecSnapshot.requestRefresh();
            throw new ErrorException("币种规则不存在或尚未同步: " + currency);
        }
        return rule;
    }
}
