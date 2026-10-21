package com.paytech.billing.domainservice.service.impl;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.paytech.billing.core.dal.dataobject.BillingFact;
import com.paytech.billing.domain.BillingFactContext;
import com.paytech.billing.domainservice.repository.BillingFactLineRepository;
import com.paytech.billing.domainservice.repository.BillingFactRefRepository;
import com.paytech.billing.domainservice.repository.BillingFactRepository;
import com.paytech.billing.domainservice.service.BillingFactPublicationService;
import com.paytech.billing.domainservice.service.CurrencyRuleService;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** BillingFactPublicationService 实现：只访问 mor_billing；发布标记为单条条件更新，不需要显式事务。 */
@Service
@AllArgsConstructor
public class BillingFactPublicationServiceImpl implements BillingFactPublicationService {
    private final CurrencyRuleService currencyRuleService;
    private final BillingFactRepository billingFactRepository;
    private final BillingFactRefRepository billingFactRefRepository;
    private final BillingFactLineRepository billingFactLineRepository;

    /** 读取事实头、引用、明细行与币种精度（Platform 币种快照，只读内存）；不存在返回空。 */
    @Override
    public BillingFactContext loadContext(long factId) {
        BillingFact fact = billingFactRepository.findById(factId);
        if (fact == null) {
            return null;
        }
        return new BillingFactContext(fact, billingFactRefRepository.findByFactId(factId),
            billingFactLineRepository.findByFactId(factId), null, minorUnit(fact));
    }

    /**
     * 已登记事实的币种精度
     * <li>本实例快照尚未包含该币种（多实例刷新先后不一致）时转为系统异常：补偿调度把业务拒绝视为终态失败，而这里应按可重试处理，
     * 待快照刷新后由补偿重试发布。</li>
     */
    int minorUnit(BillingFact fact) {
        try {
            return currencyRuleService.requireUsable(fact.getCurrency()).getMinorUnit();
        } catch (BizCheckFailException unavailable) {
            throw new ErrorException("计费事实币种规则暂不可用: 事实 " + fact.getId() + ", 币种 " + fact.getCurrency(),
                unavailable);
        }
    }

    /** 首次发布确认时写入 published_at。 */
    @Override
    public boolean markPublished(BillingFactContext context, Instant now) {
        return billingFactRepository.markPublished(context.getFact(), now);
    }
}
