package com.paytech.platform.support.referencedata.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.referencedata.core.dal.dataobject.CurrencySpec;
import com.paytech.platform.support.referencedata.domain.CurrencySpecAuditFacts;
import com.paytech.platform.support.referencedata.domain.UpdateCurrencySpecContext;
import com.paytech.platform.support.referencedata.domainservice.service.CurrencySpecManagementService;
import com.paytech.platform.support.referencedata.service.processor.request.UpdateCurrencySpecRequest;
import com.paytech.platform.support.referencedata.service.processor.response.CurrencySpecMutation;
import com.paytech.platform.support.service.processor.OperationsTransactionExecutor;

import lombok.AllArgsConstructor;

/**
 * 按原版本修改 DRAFT 币种规则的小数位与舍入方式
 * <li>启用后冻结（Q1）：ACTIVE、INACTIVE 规则拒绝修改。</li>
 * <li>无变化时不递增版本、不登记审计；版本不符返回 409，不存在返回 404。</li>
 * <li>按币种代码持资源锁，在一个本地事务内完成权限复核、行锁、版本核对、保存与审计登记。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateCurrencySpecProcessor extends AbstractLockProcessor<UpdateCurrencySpecRequest, CurrencySpecMutation> {

    private final OperationsAuditService audit;
    private final CurrencySpecManagementService service;
    private final CurrencySpecManagementSupport support;
    private final OperationsTransactionExecutor transactions;

    /** 与新建、启停共用币种代码资源锁。 */
    @Override
    protected LockCommand lockCommand(UpdateCurrencySpecRequest request) {
        return LockCommand.tryLock(CurrencySpecManagementSupport.lockKey(request.getCurrency()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected CurrencySpecMutation processWithinLock(UpdateCurrencySpecRequest request) {
        return transactions.write(() -> execute(request));
    }

    /** 授权、行锁与版本核对通过后构造 Context，保存成功才登记审计。 */
    CurrencySpecMutation execute(UpdateCurrencySpecRequest request) {
        long tenantId = support.authorize(request.getActor(), request.getSession());
        CurrencySpec spec = support.lock(request.getCurrency(), request.getExpectedVersion());
        requireDraft(spec);
        var context = new UpdateCurrencySpecContext(spec, request.getMinorUnit(), request.getRoundingMode(),
            support.now());
        if (context.unchanged()) {
            // 无变化保存不追加审计，也不递增版本。
            audit.suppressReplay();
            return CurrencySpecMutation.of(spec);
        }
        var fact = CurrencySpecAuditFacts.changed(request.getActor(), tenantId, spec, context.getMinorUnit(),
            context.getRoundingMode(), spec.getStatus(), request.getReason(), context.getUpdateTime());
        service.update(context);
        audit.record(() -> fact);
        return CurrencySpecMutation.of(spec);
    }

    /** 启用过的规则冻结小数位与舍入方式。 */
    void requireDraft(CurrencySpec spec) {
        if (spec.getStatus() != CurrencySpecStatus.DRAFT) {
            throw new BizCheckFailException("币种规则已启用过，小数位与舍入方式不可修改: " + spec.getCurrency()
                + "，当前状态 " + spec.getStatus().getCode());
        }
    }
}
