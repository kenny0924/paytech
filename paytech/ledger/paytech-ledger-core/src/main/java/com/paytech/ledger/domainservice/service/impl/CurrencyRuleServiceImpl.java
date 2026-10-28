package com.paytech.ledger.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.ledger.domain.CurrencyRule;
import com.paytech.ledger.domainservice.service.CurrencyRuleService;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.client.ReferenceCurrency;
import com.paytech.platform.support.referencedata.client.ReferenceData;

import lombok.AllArgsConstructor;

/** 从参考数据客户端的币种快照读取并按用途校验状态（Q2：INACTIVE 只拦新用途）。 */
@Service
@AllArgsConstructor
public class CurrencyRuleServiceImpl implements CurrencyRuleService {
    private final ReferenceData referenceData;

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
        if (rule.getStatus() != ReferenceDataStatus.ACTIVE) {
            throw new BizCheckFailException("币种未启用，不能用于新业务: " + currency + ", 状态 "
                + rule.getStatus().getCode());
        }
        return rule;
    }

    /**
     * 快照中没有该币种时以可重试的系统异常失败（ReferenceDataNotFoundException，属 ErrorException）
     * <li>这是快照同步滞后而非业务拒绝：AccountPosted 消费按失败重投（不进死信），下一次全量刷新后自动恢复；追平命令返回 500、不推进进度，
     * 可再次发起。快照未就绪时同样是系统异常（ReferenceDataUnavailableException）。</li>
     */
    CurrencyRule requireKnown(String currency) {
        ReferenceCurrency known = referenceData.currencies().require(currency);
        return new CurrencyRule(known.code(), known.minorUnit(), known.roundingMode(), known.status());
    }
}
