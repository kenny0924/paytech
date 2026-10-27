package com.paytech.platform.support.referencedata.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.referencedata.core.dal.dataobject.ReferenceCurrency;
import com.paytech.platform.support.referencedata.domain.CurrencySpecAuditFacts;
import com.paytech.platform.support.referencedata.domainservice.repository.ReferenceCurrencyRepository;
import com.paytech.platform.support.referencedata.domainservice.service.CurrencySpecManagementService;
import com.paytech.platform.support.referencedata.service.processor.request.CreateCurrencySpecRequest;
import com.paytech.platform.support.referencedata.service.processor.response.CurrencySpecMutation;
import com.paytech.platform.support.service.processor.OperationsTransactionExecutor;

import lombok.AllArgsConstructor;

/**
 * 新建 DRAFT 币种，版本为 1
 * <li>按币种代码持资源锁，在一个本地事务内完成权限复核、代码查重、logo 核对、保存与审计登记；logo 经文件 Owner 的同库能力读取，
 * 事务内不访问外部能力。</li>
 * <li>币种代码已存在返回 400 业务拒绝；唯一索引仍为最终保护，冲突同样转换为该拒绝。</li>
 * <li>logo 文件不存在或不是公开文件返回 422（字段 logoFileAddress）。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateCurrencySpecProcessor extends AbstractLockProcessor<CreateCurrencySpecRequest, CurrencySpecMutation> {

    private final IdGenerator ids;

    private final ReferenceDataLogos logos;

    private final OperationsAuditService audit;

    private final ReferenceCurrencyRepository repository;

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

    /** 授权、查重与 logo 核对通过后保存新行，保存成功才登记审计。 */
    CurrencySpecMutation execute(CreateCurrencySpecRequest request) {
        long tenantId = support.authorize(request.getActor(), request.getSession());
        if (repository.findByCurrencyCode(request.getCurrency()) != null) {
            throw new BizCheckFailException("币种规则已存在: " + request.getCurrency());
        }
        if (request.getLogoFileAddress() != null) {
            logos.requirePublicFile(request.getLogoFileAddress());
        }
        ReferenceCurrency currency = buildCurrency(request);
        service.create(currency);
        var fact = CurrencySpecAuditFacts.created(request.getActor(), tenantId, currency, request.getReason());
        audit.record(() -> fact);
        return CurrencySpecMutation.of(currency);
    }

    /** 插入前准备主键、DRAFT 状态、初始版本 1 与时间。 */
    ReferenceCurrency buildCurrency(CreateCurrencySpecRequest request) {
        var now = support.now();
        ReferenceCurrency currency = new ReferenceCurrency();
        currency.setId(ids.nextId());
        currency.setCurrencyCode(request.getCurrency());
        currency.setNumericCode(request.getNumericCode());
        currency.setName(request.getName());
        currency.setSymbol(request.getSymbol());
        currency.setMinorUnit(request.getMinorUnit());
        currency.setRoundingMode(request.getRoundingMode());
        currency.setLogoFileAddress(request.getLogoFileAddress());
        currency.setStatus(CurrencySpecStatus.DRAFT);
        currency.setVersion(1);
        currency.setCreateTime(now);
        currency.setUpdateTime(now);
        return currency;
    }
}
