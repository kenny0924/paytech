package com.paytech.billing.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.billing.domain.CurrencyRule;
import com.paytech.billing.domainservice.service.CurrencyRuleService;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.platform.support.referencedata.client.ReferenceCurrency;
import com.paytech.platform.support.referencedata.client.ReferenceData;

import lombok.AllArgsConstructor;

/**
 * CurrencyRuleService 实现：只读参考数据客户端的币种快照（内存），查不到时不回源，等待快照下一次全量刷新
 * <li>查不到沿用本服务原有的业务拒绝：登记命令返回 422；已登记事实的发布与自动 PAYMENT 事实登记在调用处转为可重试的系统异常。</li>
 * <li>快照未就绪时客户端抛系统异常（ReferenceDataUnavailableException），不转为业务拒绝。</li>
 */
@Service
@AllArgsConstructor
public class CurrencyRuleServiceImpl implements CurrencyRuleService {
    private final ReferenceData referenceData;

    /** 快照中没有该币种时拒绝；DRAFT 拒绝。 */
    @Override
    public CurrencyRule requireUsable(String currency) {
        ReferenceCurrency known = referenceData.currencies().find(currency)
            .orElseThrow(() -> new BizCheckFailException("币种不存在或尚未同步: " + currency
                + ", 等待 Platform 币种快照下一次刷新后重试"));
        CurrencyRule rule = new CurrencyRule(known.code(), known.minorUnit(), known.roundingMode(), known.status());
        if (!rule.usable()) {
            throw new BizCheckFailException("币种未启用: " + currency + ", 状态 " + rule.getStatus().getCode());
        }
        return rule;
    }
}
