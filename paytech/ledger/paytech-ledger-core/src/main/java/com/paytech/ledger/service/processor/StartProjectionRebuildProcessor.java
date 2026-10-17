package com.paytech.ledger.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.ledger.core.dal.dataobject.TrialBalanceException;
import com.paytech.ledger.core.enums.TrialExceptionStatus;
import com.paytech.ledger.domain.view.TrialExceptionView;
import com.paytech.ledger.domainservice.repository.TrialBalanceExceptionRepository;
import com.paytech.ledger.domainservice.service.TrialExceptionService;
import com.paytech.ledger.domainservice.validator.trial.ProjectionRebuildValidator;
import com.paytech.ledger.service.processor.request.StartProjectionRebuildRequest;

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
