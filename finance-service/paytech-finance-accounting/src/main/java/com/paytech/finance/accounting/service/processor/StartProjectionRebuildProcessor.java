package com.paytech.finance.accounting.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.finance.accounting.core.dal.dataobject.TrialBalanceException;
import com.paytech.finance.accounting.core.enums.TrialExceptionStatus;
import com.paytech.finance.accounting.domain.view.TrialExceptionView;
import com.paytech.finance.accounting.domainservice.repository.TrialBalanceExceptionRepository;
import com.paytech.finance.accounting.domainservice.service.TrialExceptionService;
import com.paytech.finance.accounting.domainservice.validator.trial.ProjectionRebuildValidator;
import com.paytech.finance.accounting.service.processor.request.StartProjectionRebuildRequest;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 以投影重建方式发起试算异常处置（Maker）
 * <li>只允许 GENERAL_LEDGER_MISMATCH/ACCOUNT 且状态为 OPEN 的异常；OPEN → UNDER_REVIEW 由条件更新保护，无需资源锁。</li>
 */
@Component
@AllArgsConstructor
public class StartProjectionRebuildProcessor
    extends AbstractProcessor<StartProjectionRebuildRequest, TrialExceptionView> {
    private final TrialExceptionService trialExceptionService;
    private final TrialBalanceExceptionRepository trialBalanceExceptionRepository;

    @Override
    protected TrialExceptionView processRequest(StartProjectionRebuildRequest request) {
        TrialBalanceException exception = trialBalanceExceptionRepository.findById(request.getExceptionId());
        ProjectionRebuildValidator.checkRebuildable(exception, request.getExceptionId(), TrialExceptionStatus.OPEN);
        trialExceptionService.startProjectionRebuild(exception, request.getMakerId());
        return TrialExceptionView.of(exception, trialExceptionService.accountMinorUnit(exception.getReferenceId()));
    }
}
