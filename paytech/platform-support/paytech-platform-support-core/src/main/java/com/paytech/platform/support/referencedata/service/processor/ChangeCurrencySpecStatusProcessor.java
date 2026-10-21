package com.paytech.platform.support.referencedata.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.referencedata.core.dal.dataobject.CurrencySpec;
import com.paytech.platform.support.referencedata.domain.ChangeCurrencySpecStatusContext;
import com.paytech.platform.support.referencedata.domain.CurrencySpecAuditFacts;
import com.paytech.platform.support.referencedata.domainservice.service.CurrencySpecManagementService;
import com.paytech.platform.support.referencedata.service.processor.request.ChangeCurrencySpecStatusRequest;
import com.paytech.platform.support.referencedata.service.processor.response.CurrencySpecMutation;
import com.paytech.platform.support.service.processor.OperationsTransactionExecutor;

import lombok.AllArgsConstructor;

/**
 * 按原版本切换币种规则状态
 * <li>只允许 DRAFT→ACTIVE、ACTIVE→INACTIVE、INACTIVE→ACTIVE；同状态与 DRAFT→INACTIVE 拒绝。</li>
 * <li>版本不符返回 409，不存在返回 404；单人操作，无复核（Q4）。</li>
 * <li>按币种代码持资源锁，在一个本地事务内完成权限复核、行锁、版本核对、保存与审计登记。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ChangeCurrencySpecStatusProcessor
    extends AbstractLockProcessor<ChangeCurrencySpecStatusRequest, CurrencySpecMutation> {

    private final OperationsAuditService audit;
    private final CurrencySpecManagementService service;
    private final CurrencySpecManagementSupport support;
    private final OperationsTransactionExecutor transactions;

    /** 与新建、修改共用币种代码资源锁。 */
    @Override
    protected LockCommand lockCommand(ChangeCurrencySpecStatusRequest request) {
        return LockCommand.tryLock(CurrencySpecManagementSupport.lockKey(request.getCurrency()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected CurrencySpecMutation processWithinLock(ChangeCurrencySpecStatusRequest request) {
        return transactions.write(() -> execute(request));
    }

    /** 授权、行锁、版本与转换核对通过后保存，保存成功才登记审计。 */
    CurrencySpecMutation execute(ChangeCurrencySpecStatusRequest request) {
        long tenantId = support.authorize(request.getActor(), request.getSession());
        CurrencySpec spec = support.lock(request.getCurrency(), request.getExpectedVersion());
        var context = new ChangeCurrencySpecStatusContext(spec, request.getStatus(), support.now());
        requireTransitionAllowed(context);
        var fact = CurrencySpecAuditFacts.changed(request.getActor(), tenantId, spec, spec.getMinorUnit(),
            spec.getRoundingMode(), context.getStatus(), request.getReason(), context.getUpdateTime());
        service.changeStatus(context);
        audit.record(() -> fact);
        return CurrencySpecMutation.of(spec);
    }

    /** 不允许的转换带上币种与前后状态拒绝。 */
    void requireTransitionAllowed(ChangeCurrencySpecStatusContext context) {
        if (!context.transitionAllowed()) {
            throw new BizCheckFailException("币种规则状态不能从 " + context.getSpec().getStatus().getCode() + " 变为 "
                + context.getStatus().getCode() + ": " + context.getSpec().getCurrency());
        }
    }
}
