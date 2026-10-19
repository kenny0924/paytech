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
 * 批准：PENDING_APPROVAL → APPROVED（technique.md §2.1 本地 Maker-Checker）
 * <li>Maker 取自版本最近一次提交快照的 create_by，Checker 必须不同于 Maker（否则 400）；审批只记录"人已同意"，排期与激活时重新执行
 * 全部校验。</li>
 * <li>方案锁持续等待，根 CAS 保护并发。</li>
 */
@Component
@AllArgsConstructor
public class ApproveCommercialTermVersionProcessor
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
        commercialTermService.approve(context);
        return context.toVersionView();
    }
}
