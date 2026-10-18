package com.paytech.ledger.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.ledger.core.constant.AccountingLocks;
import com.paytech.ledger.core.dal.dataobject.TrialBalanceException;
import com.paytech.ledger.core.enums.TrialExceptionStatus;
import com.paytech.ledger.domain.view.TrialExceptionView;
import com.paytech.ledger.domainservice.repository.TrialBalanceExceptionRepository;
import com.paytech.ledger.domainservice.service.TrialExceptionService;
import com.paytech.ledger.domainservice.validator.trial.ProjectionRebuildValidator;
import com.paytech.ledger.service.processor.request.ApproveProjectionRebuildRequest;

import lombok.AllArgsConstructor;

/**
 * 批准试算异常的投影重建（Checker）
 * <li>同一异常的批准按资源锁串行；状态迁移仍以条件更新为最终防线。</li>
 * <li>复核人必须不同于编制人；只写 t_trial_balance_exception（冻结引用账户按分录聚合的值），不写 Journal、不写余额、不改变序号（design/04 §4）。</li>
 */
@Component
@AllArgsConstructor
public class ApproveProjectionRebuildProcessor
    extends AbstractLockProcessor<ApproveProjectionRebuildRequest, TrialExceptionView> {
    private final TrialExceptionService trialExceptionService;
    private final TrialBalanceExceptionRepository trialBalanceExceptionRepository;

    /** 与同一异常的其他写入口共用资源锁，未取得立即返回并发冲突。 */
    @Override
    protected LockCommand lockCommand(ApproveProjectionRebuildRequest request) {
        return LockCommand.tryLock(AccountingLocks.trialException(request.getExceptionId()));
    }

    @Override
    protected TrialExceptionView processWithinLock(ApproveProjectionRebuildRequest request) {
        TrialBalanceException exception = trialBalanceExceptionRepository.findById(request.getExceptionId());
        ProjectionRebuildValidator.checkRebuildable(exception, request.getExceptionId(),
            TrialExceptionStatus.UNDER_REVIEW);
        ProjectionRebuildValidator.checkChecker(exception, request.getCheckerId());
        int minorUnit = trialExceptionService.accountMinorUnit(exception.getReferenceId());
        trialExceptionService.approveProjectionRebuild(exception, request.getCheckerId(), minorUnit);
        return TrialExceptionView.of(exception, minorUnit);
    }
}
