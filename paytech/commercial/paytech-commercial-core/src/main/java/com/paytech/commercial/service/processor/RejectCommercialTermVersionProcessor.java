package com.paytech.commercial.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.CommercialTermVersionContext;
import com.paytech.commercial.domain.CommercialTermVersionView;
import com.paytech.commercial.domainservice.service.CommercialTermService;
import com.paytech.commercial.service.processor.request.CommercialTermVersionCommandRequest;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 驳回：PENDING_APPROVAL → DRAFT，Checker 必须不同于 Maker（否则 400）；驳回后可再次修改价格并重新提交
 * <li>方案锁持续等待，根 CAS 保护并发。</li>
 */
@Component
@AllArgsConstructor
public class RejectCommercialTermVersionProcessor
    extends AbstractLockProcessor<CommercialTermVersionCommandRequest, CommercialTermVersionView> {
    private final CommercialTermLoader loader;
    private final CommercialTermService commercialTermService;
    private final CommercialTermReviewSupport reviewSupport;

    /** 方案资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(CommercialTermVersionCommandRequest request) {
        return LockCommand.lock(CommercialLocks.termPlan(loader.planIdOf(request.getVersionId())));
    }

    @Override
    protected CommercialTermVersionView processWithinLock(CommercialTermVersionCommandRequest request) {
        CommercialTermVersion version = loader.version(request.getVersionId());
        CommercialTermVersionContext context = reviewSupport.reviewContext(request, version);
        commercialTermService.reject(context);
        return context.toVersionView();
    }
}
