package com.paytech.ledger.domainservice.repository.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundle;
import com.paytech.ledger.core.dal.mapper.AccountingRuleBundleMapper;
import com.paytech.ledger.domainservice.repository.AccountingRuleBundleRepository;

import lombok.AllArgsConstructor;

/** AccountingRuleBundleRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class AccountingRuleBundleRepositoryImpl implements AccountingRuleBundleRepository {
    private final Clock clock;
    private final AccountingRuleBundleMapper mapper;

    /** 按代码读取规则集，不存在返回空。 */
    @Override
    public AccountingRuleBundle findByCode(String bundleCode) {
        return mapper.selectByCode(bundleCode);
    }

    /** 按代码读取并加排他锁，不存在返回空。 */
    @Override
    public AccountingRuleBundle lockByCode(String bundleCode) {
        return mapper.selectByCodeForUpdate(bundleCode);
    }

    /** 以期望版本条件切换当前版本指针，成功后同步原对象。 */
    @Override
    public void switchCurrentVersion(AccountingRuleBundle bundle, long currentVerId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (mapper.updateCurrentVersion(bundle, currentVerId, now) != 1) {
            throw new ConcurrentRequestException("规则集当前版本已变化: " + bundle.getBundleCode());
        }
        bundle.setCurrentVerId(currentVerId);
        bundle.setVersion(bundle.getVersion() + 1);
        bundle.setUpdateTime(now);
    }
}
