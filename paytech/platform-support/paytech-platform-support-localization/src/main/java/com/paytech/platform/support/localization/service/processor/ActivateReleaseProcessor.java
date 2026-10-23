package com.paytech.platform.support.localization.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.api.localization.response.CurrentReleaseResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nRelease;
import com.paytech.platform.support.localization.domainservice.repository.I18nReleaseRepository;
import com.paytech.platform.support.localization.domainservice.service.ReleaseService;
import com.paytech.platform.support.localization.service.processor.request.ActivateReleaseCommand;

import lombok.AllArgsConstructor;

/**
 * 切换应用当前发布（上线或回滚，技术方案 3.3），写审计 I18N_RELEASE_ACTIVATE
 * <li>持应用资源锁；目标须为 READY（否则 400），不生成新文件。写事务内锁应用行、按原当前发布条件更新；已是当前发布时不写入、不写审计。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ActivateReleaseProcessor
    extends AbstractLockProcessor<ActivateReleaseCommand, CurrentReleaseResponseBody> {

    private final ReleaseService releaseService;
    private final I18nReleaseRepository releases;
    private final LocalizationProcessorSupport support;

    @Override
    protected LockCommand lockCommand(ActivateReleaseCommand request) {
        return LockCommand.tryLock(LocalizationProcessorSupport.applicationLock(request.getApplicationCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected CurrentReleaseResponseBody processWithinLock(ActivateReleaseCommand request) {
        SessionContext session = support.authorize(request.getActor(), request.getSession(),
            LocalizationProcessorSupport.PUBLISH_PERMISSION);
        I18nApplication application = support.requireApplication(request.getApplicationCode());
        I18nRelease release = releases.findByNo(application.getApplicationId(), request.getReleaseNo());
        if (release == null) {
            throw LocalizationProcessorSupport.notFound();
        }
        support.write(request.getActor(), request.getSession(), LocalizationProcessorSupport.PUBLISH_PERMISSION, () -> {
            if (releaseService.activateRelease(application, release, support.now())) {
                support.recordRelease(session, release.getReleaseId(), "切换到发布 " + release.getReleaseNo() + "："
                    + request.getReason());
            }
            return null;
        });
        CurrentReleaseResponseBody body = new CurrentReleaseResponseBody();
        body.setApplicationCode(application.getApplicationCode());
        body.setReleaseNo(release.getReleaseNo());
        return body;
    }
}
