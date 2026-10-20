package com.paytech.ledger.domainservice.repository.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.core.dal.mapper.AccountingRuleBundleVerMapper;
import com.paytech.ledger.core.enums.RuleVersionStatus;
import com.paytech.ledger.domainservice.repository.AccountingRuleBundleVerRepository;

import lombok.AllArgsConstructor;

/** AccountingRuleBundleVerRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class AccountingRuleBundleVerRepositoryImpl implements AccountingRuleBundleVerRepository {
    private final Clock clock;
    private final AccountingRuleBundleVerMapper mapper;

    /** 按主键读取，不存在返回空。 */
    @Override
    public AccountingRuleBundleVer findById(long id) {
        return mapper.selectById(id);
    }

    /** 按规则集与版本号读取，不存在返回空。 */
    @Override
    public AccountingRuleBundleVer findByBundleVer(long bundleId, long bundleVer) {
        return mapper.selectByBundleVer(bundleId, bundleVer);
    }

    /** 按规则集与版本号读取并加排他锁，不存在返回空。 */
    @Override
    public AccountingRuleBundleVer lockByBundleVer(long bundleId, long bundleVer) {
        return mapper.selectByBundleVerForUpdate(bundleId, bundleVer);
    }

    /** 规则集全部版本，按版本号降序。 */
    @Override
    public List<AccountingRuleBundleVer> findByBundle(long bundleId) {
        return mapper.selectByBundle(bundleId);
    }

    /** 已到生效时间的 APPROVED 版本，按生效时间、规则集、版本号升序。 */
    @Override
    public List<AccountingRuleBundleVer> findDueApproved(Instant asOf) {
        return mapper.selectDueApproved(asOf);
    }

    /** 规则集的下一个版本号。 */
    @Override
    public long nextBundleVer(long bundleId) {
        return mapper.selectNextBundleVer(bundleId);
    }

    /** 规则集是否已有 DRAFT 或 PENDING_APPROVAL 版本。 */
    @Override
    public boolean existsOpenVersion(long bundleId) {
        return mapper.countOpenVersions(bundleId) > 0;
    }

    /** 写入新版本；版本号重复时按并发冲突拒绝。 */
    @Override
    public void insert(AccountingRuleBundleVer ver) {
        try {
            mapper.insert(ver);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("规则版本号已存在: 规则集 " + ver.getBundleId() + ", 版本 "
                + ver.getBundleVer(), e);
        }
    }

    /** 以原状态为条件迁移状态，成功后同步原对象。 */
    @Override
    public void updateStatus(AccountingRuleBundleVer ver, RuleVersionStatus status) {
        transit(ver, status, ver.getCheckerId(), ver.getApprovedTime(), ver.getEffectiveFrom(), ver.getEffectiveTo());
    }

    /** 以 PENDING_APPROVAL 为条件写入复核结果与生效时间，成功后同步原对象。 */
    @Override
    public void review(AccountingRuleBundleVer ver, RuleVersionStatus status, long checkerId, Instant approvedTime,
        Instant effectiveFrom) {
        requireStatus(ver, RuleVersionStatus.PENDING_APPROVAL);
        transit(ver, status, checkerId, approvedTime, effectiveFrom, ver.getEffectiveTo());
    }

    /** 以 APPROVED 为条件置为 ACTIVE，复核结果不变，成功后同步原对象。 */
    @Override
    public void activate(AccountingRuleBundleVer ver) {
        requireStatus(ver, RuleVersionStatus.APPROVED);
        transit(ver, RuleVersionStatus.ACTIVE, ver.getCheckerId(), ver.getApprovedTime(), ver.getEffectiveFrom(),
            ver.getEffectiveTo());
    }

    /** 以 APPROVED 为条件置为 RETIRED，失效时间等于自身生效时间（零长度区间），成功后同步原对象。 */
    @Override
    public void retireSuperseded(AccountingRuleBundleVer ver) {
        requireStatus(ver, RuleVersionStatus.APPROVED);
        transit(ver, RuleVersionStatus.RETIRED, ver.getCheckerId(), ver.getApprovedTime(), ver.getEffectiveFrom(),
            ver.getEffectiveFrom());
    }

    /** 以 ACTIVE 为条件置为 RETIRED 并写入失效时间，成功后同步原对象。 */
    @Override
    public void retire(AccountingRuleBundleVer ver, Instant effectiveTo) {
        requireStatus(ver, RuleVersionStatus.ACTIVE);
        transit(ver, RuleVersionStatus.RETIRED, ver.getCheckerId(), ver.getApprovedTime(), ver.getEffectiveFrom(),
            effectiveTo);
    }

    /** 以对象当前状态为条件执行迁移（含生效与失效时间），行数不为 1 时按并发冲突拒绝，成功后同步原对象。 */
    void transit(AccountingRuleBundleVer ver, RuleVersionStatus status, Long checkerId, Instant approvedTime,
        Instant effectiveFrom, Instant effectiveTo) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (mapper.updateStatus(ver, status, checkerId, approvedTime, effectiveFrom, effectiveTo, now) != 1) {
            throw new ConcurrentRequestException("规则版本状态已变化: " + ver.getId());
        }
        ver.setStatus(status);
        ver.setCheckerId(checkerId);
        ver.setApprovedTime(approvedTime);
        ver.setEffectiveFrom(effectiveFrom);
        ver.setEffectiveTo(effectiveTo);
        ver.setUpdateTime(now);
    }

    /** 迁移前对象状态必须为期望的原状态。 */
    void requireStatus(AccountingRuleBundleVer ver, RuleVersionStatus expected) {
        if (ver.getStatus() != expected) {
            throw new ConcurrentRequestException("规则版本状态已变化: " + ver.getId() + ", 状态 " + ver.getStatus().getCode());
        }
    }
}
