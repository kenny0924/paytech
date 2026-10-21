package com.paytech.platform.support.referencedata.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.referencedata.core.dal.dataobject.CurrencySpec;
import com.paytech.platform.support.referencedata.domain.CurrencySpecAuditFacts;
import com.paytech.platform.support.referencedata.domainservice.repository.CurrencySpecRepository;
import com.paytech.platform.support.referencedata.domainservice.service.CurrencySpecManagementService;
import com.paytech.platform.support.referencedata.service.processor.request.CreateCurrencySpecRequest;
import com.paytech.platform.support.referencedata.service.processor.response.CurrencySpecMutation;
import com.paytech.platform.support.service.processor.OperationsTransactionExecutor;

import lombok.AllArgsConstructor;

/**
 * 新建 DRAFT 币种规则，版本为 1
 * <li>按币种代码持资源锁，在一个本地事务内完成权限复核、代码查重、保存与审计登记；事务内不访问外部能力。</li>
 * <li>币种代码已存在返回 400 业务拒绝；唯一索引仍为最终保护，冲突同样转换为该拒绝。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateCurrencySpecProcessor extends AbstractLockProcessor<CreateCurrencySpecRequest, CurrencySpecMutation> {

    private final IdGenerator ids;
    private final CurrencySpecRepository repository;
    private final OperationsAuditService audit;
    private final CurrencySpecManagementService service;
    private final CurrencySpecManagementSupport support;
    private final OperationsTransactionExecutor transactions;

    /** 与修改、启停共用币种代码资源锁。 */
    @Override
    protected LockCommand lockCommand(CreateCurrencySpecRequest request) {
        return LockCommand.tryLock(CurrencySpecManagementSupport.lockKey(request.getCurrency()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected CurrencySpecMutation processWithinLock(CreateCurrencySpecRequest request) {
        return transactions.write(() -> execute(request));
    }

    /** 授权与查重通过后保存新行，保存成功才登记审计。 */
    CurrencySpecMutation execute(CreateCurrencySpecRequest request) {
        long tenantId = support.authorize(request.getActor(), request.getSession());
        if (repository.findByCurrency(request.getCurrency()) != null) {
            throw new BizCheckFailException("币种规则已存在: " + request.getCurrency());
        }
        CurrencySpec spec = buildCurrencySpec(request);
        service.create(spec);
        var fact = CurrencySpecAuditFacts.created(request.getActor(), tenantId, spec, request.getReason());
        audit.record(() -> fact);
        return CurrencySpecMutation.of(spec);
    }

    /** 插入前准备主键、DRAFT 状态、初始版本 1 与时间。 */
    CurrencySpec buildCurrencySpec(CreateCurrencySpecRequest request) {
        var now = support.now();
        CurrencySpec spec = new CurrencySpec();
        spec.setId(ids.nextId());
        spec.setCurrency(request.getCurrency());
        spec.setMinorUnit(request.getMinorUnit());
        spec.setRoundingMode(request.getRoundingMode());
        spec.setStatus(CurrencySpecStatus.DRAFT);
        spec.setVersion(1);
        spec.setCreateTime(now);
        spec.setUpdateTime(now);
        return spec;
    }
}
