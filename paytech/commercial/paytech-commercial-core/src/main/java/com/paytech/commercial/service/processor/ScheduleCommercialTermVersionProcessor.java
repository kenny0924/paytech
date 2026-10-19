package com.paytech.commercial.service.processor;

import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.CommercialTermVersionContext;
import com.paytech.commercial.domain.CommercialTermVersionView;
import com.paytech.commercial.domainservice.service.CommercialTermService;
import com.paytech.commercial.domainservice.validator.term.TermVersionActivationValidator;
import com.paytech.commercial.service.processor.request.CommercialTermVersionCommandRequest;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 排期：APPROVED → SCHEDULED 并写入生效起点（technique.md §2.2、§2.6）
 * <li>锁内一次读取价格集合、画像、开通项与同方案版本，执行激活校验 1–5（生效起点不早于当前时间）；任一失败 400。</li>
 * <li>版本按商业时点窗口选取（§2.3），因此同一事务把前一 ACTIVE 版本的生效终点写成新起点（状态仍 ACTIVE，激活时才转 SUPERSEDED）；
 * 已有其他 SCHEDULED 版本时拒绝，同方案同时至多一个待生效版本。</li>
 * <li>方案锁持续等待，根 CAS 保护并发。</li>
 */
@Component
@AllArgsConstructor
public class ScheduleCommercialTermVersionProcessor
    extends AbstractLockProcessor<CommercialTermVersionCommandRequest, CommercialTermVersionView> {
    private final CommercialTermLoader loader;
    private final CommercialTermService commercialTermService;

    /** 排期必须给出生效起点。 */
    @Override
    protected void validate(CommercialTermVersionCommandRequest request) {
        if (request.getEffectiveFrom() == null || request.systemCommand()) {
            throw new InvalidParameterException("排期必须由操作人给出 effectiveFrom");
        }
    }

    /** 方案资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(CommercialTermVersionCommandRequest request) {
        return LockCommand.lock(CommercialLocks.termPlan(loader.planIdOf(request.getVersionId())));
    }

    @Override
    protected CommercialTermVersionView processWithinLock(CommercialTermVersionCommandRequest request) {
        // 构建上下文（含激活校验输入）
        CommercialTermVersion version = loader.version(request.getVersionId());
        CommercialTermVersionContext.CommercialTermVersionContextBuilder builder = loader.commandContext(request, version)
            .effectiveFrom(request.getEffectiveFrom().truncatedTo(ChronoUnit.MICROS));
        CommercialTermVersionContext context = loader.previousActive(
            loader.activationInputs(builder, loader.plan(version.getTermPlanId()), version), version).build();

        // 激活校验 1–5
        TermVersionActivationValidator.checkSchedule(context, context.getEffectiveFrom());

        // 保存
        commercialTermService.schedule(context);
        return context.toVersionView();
    }
}
