package com.paytech.payment.execution.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.payment.execution.core.dal.dataobject.PaymentAttempt;
import com.paytech.payment.execution.domain.ApplyCardInstrumentContext;
import com.paytech.payment.execution.domain.CreateIntentContext;
import com.paytech.payment.execution.domain.PayContext;
import com.paytech.payment.execution.domain.TerminateIntentContext;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentInstrumentTokenRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;
import com.paytech.payment.execution.service.PaymentExecutionService;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class PaymentExecutionServiceImpl implements PaymentExecutionService {
    private final TransactionTemplate transactionTemplate;
    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentInstrumentTokenRepository paymentInstrumentTokenRepository;

    /** 保存工具绑定，参与申请 Processor 的事务 */
    @Override
    public void saveApplyCardInstrumentContext(ApplyCardInstrumentContext context) {
        paymentInstrumentTokenRepository.insertToken(context.getInstrumentToken());
    }

    @Override
    public void saveCreateIntentContext(CreateIntentContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            paymentIntentRepository.insertIntent(context.getIntent());
        });
    }

    /** 终止条件由 Processor 确认，写入时以状态和版本保护并发。 */
    @Override
    public void terminateIntent(TerminateIntentContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            paymentIntentRepository.terminateIntent(context.getIntent());
        });
    }

    /** 按主键读取支付尝试。 */
    @Override
    public PaymentAttempt findAttempt(long attemptId) {
        return paymentAttemptRepository.selectById(attemptId);
    }

    @Override
    public void savePayContext(PayContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            paymentAttemptRepository.insertAttempt(context.getAttempt());
            paymentIntentRepository.bindAttempt(context.getIntent(), context.getAttempt());
        });
    }
}
