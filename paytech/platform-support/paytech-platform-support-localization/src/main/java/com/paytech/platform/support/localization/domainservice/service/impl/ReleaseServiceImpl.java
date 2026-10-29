package com.paytech.platform.support.localization.domainservice.service.impl;

import java.time.Instant;
import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.platform.support.api.enums.LocalizationErrorCode;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nRelease;
import com.paytech.platform.support.localization.core.enums.ReleaseStatus;
import com.paytech.platform.support.localization.domain.PublishContext;
import com.paytech.platform.support.localization.domain.ReleaseCheckTarget;
import com.paytech.platform.support.localization.domainservice.repository.I18nApplicationRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nReleaseBundleRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nReleaseChangeRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nReleaseRepository;
import com.paytech.platform.support.localization.domainservice.service.ReleaseService;

/** 发布登记、检查结论与当前发布切换；每次写入先锁应用行，状态与指针都以条件更新保护。 */
@Service
public class ReleaseServiceImpl implements ReleaseService {

    private final I18nReleaseRepository releases;
    private final I18nReleaseBundleRepository bundles;
    private final I18nReleaseChangeRepository changes;
    private final I18nApplicationRepository applications;
    private final ObjectProvider<CompensationEventRegistrar> registrar;

    /**
     * 装配依赖；补偿登记器只在 compensation.enabled 时存在。
     *
     * @param releases 发布仓储
     * @param bundles 语言包仓储
     * @param changes 发布文案变化仓储
     * @param applications 应用仓储
     * @param registrar 补偿登记器
     */
    public ReleaseServiceImpl(I18nReleaseRepository releases, I18nReleaseBundleRepository bundles,
        I18nReleaseChangeRepository changes, I18nApplicationRepository applications,
        ObjectProvider<CompensationEventRegistrar> registrar) {
        this.releases = releases;
        this.bundles = bundles;
        this.changes = changes;
        this.applications = applications;
        this.registrar = registrar;
    }

    /** 发布不存在返回 null。 */
    @Override
    public ReleaseCheckTarget loadCheckTarget(long releaseId) {
        I18nRelease release = releases.findById(releaseId);
        return release == null ? null : new ReleaseCheckTarget(release, bundles.findByRelease(releaseId));
    }

    /** 文案修订号未变才登记；发布（含变化条数）、语言包、文案变化与上线任务同事务提交。 */
    @Override
    public void registerRelease(PublishContext context) {
        I18nApplication current = lock(context.getApplication());
        if (!Objects.equals(current.getContentRevision(), context.getApplication().getContentRevision())) {
            throw new FailException(LocalizationErrorCode.I18N_CONTENT_CHANGED);
        }
        releases.insert(context.getRelease());
        bundles.insertAll(context.getBundles());
        changes.insertAll(context.getChanges());
        registrar().register(context.getActivationEvent());
    }

    /** 标为 READY 后按发布号决定是否上线。 */
    @Override
    public boolean completeCheck(I18nRelease release, Instant now) {
        I18nApplication application = applications.lockById(release.getApplicationId());
        if (application == null) {
            throw new ErrorException("发布所属应用不存在: " + release.getReleaseId());
        }
        releases.markReady(release, now);
        if (!isNewerThanCurrent(application, release)) {
            return false;
        }
        applications.switchCurrentRelease(application, release.getReleaseId(), now);
        return true;
    }

    /** 标为 FAILED。 */
    @Override
    public void failCheck(I18nRelease release, String reason, Instant now) {
        releases.markFailed(release, reason, now);
    }

    /** 目标须为 READY；已是当前发布时不写入。 */
    @Override
    public boolean activateRelease(I18nApplication application, I18nRelease release, Instant now) {
        I18nApplication current = lock(application);
        if (release.getStatus() != ReleaseStatus.READY) {
            throw new BizCheckFailException("只能切换到已上线检查通过（READY）的发布: " + release.getReleaseNo()
                + "，当前状态 " + release.getStatus().getCode());
        }
        if (Objects.equals(current.getCurrentReleaseId(), release.getReleaseId())) {
            return false;
        }
        applications.switchCurrentRelease(current, release.getReleaseId(), now);
        return true;
    }

    /** 应用当前没有发布，或当前发布号小于该发布。 */
    boolean isNewerThanCurrent(I18nApplication application, I18nRelease release) {
        if (application.getCurrentReleaseId() == null) {
            return true;
        }
        I18nRelease current = releases.findById(application.getCurrentReleaseId());
        return current == null || current.getReleaseNo() < release.getReleaseNo();
    }

    /** 锁应用行；不存在说明被并发删除。 */
    I18nApplication lock(I18nApplication application) {
        I18nApplication current = applications.lockById(application.getApplicationId());
        if (current == null) {
            throw new ConcurrentRequestException("应用已不存在: " + application.getApplicationCode());
        }
        return current;
    }

    /** 补偿登记器；发布上线必须可靠执行，未开启 compensation.enabled 时明确失败并回滚。 */
    CompensationEventRegistrar registrar() {
        CompensationEventRegistrar available = registrar.getIfAvailable();
        if (available == null) {
            throw new ErrorException("多语言发布需要开启 compensation.enabled");
        }
        return available;
    }
}
