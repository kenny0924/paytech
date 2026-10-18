package com.paytech.billing.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.billing.core.constant.BillingLocks;
import com.paytech.billing.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.billing.core.enums.ChannelCostVersionStatus;
import com.paytech.billing.domain.ChannelCostVersionContext;
import com.paytech.billing.domain.view.ChannelCostVersionView;
import com.paytech.billing.domainservice.service.ChannelCostPlanService;
import com.paytech.billing.service.processor.request.SubmitChannelCostVersionRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 提交渠道成本版本待复核：DRAFT → PENDING_APPROVAL；内容与摘要创建时已冻结
 * <li>只有编制人可以提交。</li>
 * <li>与同一方案的其他写入口共用资源锁并持续等待；事务内先锁方案再锁版本，状态以原状态条件更新。</li>
 */
@Component
@AllArgsConstructor
public class SubmitChannelCostVersionProcessor
    extends AbstractLockProcessor<SubmitChannelCostVersionRequest, ChannelCostVersionView> {
    private final ChannelCostPlanService channelCostPlanService;

    /** 方案资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(SubmitChannelCostVersionRequest request) {
        return LockCommand.lock(BillingLocks.channelCostPlan(request.getPlanCode()));
    }

    @Override
    protected ChannelCostVersionView processWithinLock(SubmitChannelCostVersionRequest request) {
        ChannelCostVersionContext context =
            channelCostPlanService.loadVersion(request.getPlanCode(), request.getCostVer());
        checkSubmittable(request, context.getVer());
        channelCostPlanService.submitVersion(context);
        return context.toView();
    }

    /** 只能提交 DRAFT 版本，且提交人为编制人。 */
    void checkSubmittable(SubmitChannelCostVersionRequest request, ChannelCostPlanVer ver) {
        if (ver.getStatus() != ChannelCostVersionStatus.DRAFT) {
            throw new BizCheckFailException("渠道成本版本不是草稿: 方案 " + request.getPlanCode() + ", 版本 "
                + request.getCostVer() + ", 状态 " + ver.getStatus().getCode());
        }
        if (!ver.getMakerId().equals(request.getMakerId())) {
            throw new BizCheckFailException("提交人必须是编制人: 方案 " + request.getPlanCode() + ", 版本 "
                + request.getCostVer() + ", 编制人 " + ver.getMakerId() + ", 提交人 " + request.getMakerId());
        }
    }
}
