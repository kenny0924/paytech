package com.paytech.commercial.service.processor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.CommercialTermVersionContext;
import com.paytech.commercial.domain.CommercialTermVersionView;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domainservice.service.CommercialTermService;
import com.paytech.commercial.domainservice.validator.term.TermVersionActivationValidator;
import com.paytech.commercial.service.handler.CommercialTermVersionActivatedHandler;
import com.paytech.commercial.service.processor.request.CommercialTermVersionCommandRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 激活：SCHEDULED → ACTIVE（生效起点已到），前一 ACTIVE → SUPERSEDED 且生效终点等于新版本起点（technique.md §2.2、§2.6、§2.8）
 * <li>运营手工触发（POST .../activate，携带根版本）与定时作业 commercialTermVersionActivation（系统命令，锁内读取根版本、操作人
 * 记 0）执行同一用例。</li>
 * <li>锁内重新执行激活校验 1–5；同方案存在更早的未激活排期版本时拒绝，作业按生效起点顺序逐个激活。</li>
 * <li>一个本地事务：根 CAS、先释放旧槽位再占新槽位、方案首次生效转 ACTIVE、审计快照，并登记 CommercialTermVersionActivated
 * 发布任务；提交后由补偿执行器发布，事务回滚则不发布。</li>
 * <li>标准价目模板版本不参与运行时取价，激活不登记 CommercialTermVersionActivated 发布任务。</li>
 */
@Component
@AllArgsConstructor
public class ActivateCommercialTermVersionProcessor
    extends AbstractLockProcessor<CommercialTermVersionCommandRequest, CommercialTermVersionView> {
    private final TransactionTemplate transactionTemplate;
    private final CommercialTermLoader loader;
    private final CommercialTermService commercialTermService;
    private final CompensationEventRegistrar compensationEventRegistrar;

    /** 方案资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(CommercialTermVersionCommandRequest request) {
        return LockCommand.lock(CommercialLocks.termPlan(loader.planIdOf(request.getVersionId())));
    }

    @Override
    protected CommercialTermVersionView processWithinLock(CommercialTermVersionCommandRequest request) {
        // 构建上下文（含激活校验输入与被替代版本）
        CommercialTermVersionContext context = buildContext(request);

        // 激活校验 1–5
        TermVersionActivationValidator.checkActivate(context);
        checkNoEarlierSchedule(context);

        // 保存并登记发布任务
        transactionTemplate.executeWithoutResult(status -> {
            commercialTermService.activate(context);
            if (!context.template()) {
                compensationEventRegistrar.register(CommercialTermVersionActivatedHandler.buildEvent(
                    context.getPlan().getTermPlanId(), context.nextChangeVersion()));
            }
        });
        return context.toVersionView();
    }

    /** 公共部分、激活校验输入与前一 ACTIVE 版本及其 Child。 */
    CommercialTermVersionContext buildContext(CommercialTermVersionCommandRequest request) {
        CommercialTermVersion version = loader.version(request.getVersionId());
        CommercialTermPlan plan = loader.plan(version.getTermPlanId());
        return loader.previousActive(loader.activationInputs(loader.commandContext(request, version), plan, version),
            version).build();
    }

    /** 同方案不得有生效起点更早、尚未激活的 SCHEDULED 版本。 */
    void checkNoEarlierSchedule(CommercialTermVersionContext context) {
        CommercialTermVersion version = context.getVersion();
        boolean earlier = context.getPlanVersions().stream()
            .anyMatch(other -> other.getStatus() == CommercialTermVersionStatus.SCHEDULED
                && other.getEffectiveFrom().isBefore(version.getEffectiveFrom()));
        if (earlier) {
            throw new BizCheckFailException("同方案存在生效起点更早的已排期版本，须先激活: " + version.getTermVersionId());
        }
    }
}
