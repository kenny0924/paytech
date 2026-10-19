package com.paytech.commercial.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.CommercialTermVersionContext;
import com.paytech.commercial.domain.CommercialTermVersionView;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domainservice.service.CommercialTermService;
import com.paytech.commercial.service.processor.request.CommercialTermVersionCommandRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 提交审批：DRAFT → PENDING_APPROVAL，操作人记为 Maker 写入版本审计快照（technique.md §2.1 本地 Maker-Checker）
 * <li>版本至少有一条价格；方案锁持续等待，根 CAS 保护并发。</li>
 */
@Component
@AllArgsConstructor
public class SubmitCommercialTermVersionProcessor
    extends AbstractLockProcessor<CommercialTermVersionCommandRequest, CommercialTermVersionView> {
    private final CommercialTermLoader loader;
    private final CommercialTermService commercialTermService;

    /** 方案资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(CommercialTermVersionCommandRequest request) {
        return LockCommand.lock(CommercialLocks.termPlan(loader.planIdOf(request.getVersionId())));
    }

    @Override
    protected CommercialTermVersionView processWithinLock(CommercialTermVersionCommandRequest request) {
        CommercialTermVersion version = loader.version(request.getVersionId());
        if (version.getStatus() != CommercialTermVersionStatus.DRAFT) {
            throw new BizCheckFailException("只有 DRAFT 版本可以提交: " + version.getTermVersionId() + ", 状态 "
                + version.getStatus().getCode());
        }
        if (loader.prices(version.getTermVersionId()).isEmpty()) {
            throw new BizCheckFailException("版本没有价格，不能提交: " + version.getTermVersionId());
        }
        CommercialTermVersionContext context = loader.commandContext(request, version).build();
        commercialTermService.submit(context);
        return context.toVersionView();
    }
}
