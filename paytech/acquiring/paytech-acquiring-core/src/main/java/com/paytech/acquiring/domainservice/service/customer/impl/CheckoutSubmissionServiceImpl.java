package com.paytech.acquiring.domainservice.service.customer.impl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.paytech.acquiring.domain.customer.SubmitCustomerPaymentContext;
import com.paytech.acquiring.domainservice.repository.customer.CheckoutNetworkEvidenceRepository;
import com.paytech.acquiring.domainservice.repository.customer.ConsumerAcceptanceRepository;
import com.paytech.acquiring.domainservice.repository.order.CommerceOrderRepository;
import com.paytech.acquiring.domainservice.repository.order.OrderPaymentProjectionRepository;
import com.paytech.acquiring.domainservice.service.customer.CheckoutSubmissionService;
import com.paytech.acquiring.service.transaction.CommerceTransactionExecutor;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** 接收已验证上下文，只保存所属本库事实，外部支付在提交后执行。 */
@Service
@AllArgsConstructor
public class CheckoutSubmissionServiceImpl implements CheckoutSubmissionService {
    private final Clock clock;
    private final OrderPaymentProjectionRepository paymentRepository;
    private final CommerceOrderRepository orderRepository;
    private final CommerceTransactionExecutor transactions;
    private final ConsumerAcceptanceRepository acceptanceRepository;
    private final CheckoutNetworkEvidenceRepository evidenceRepository;

    /** REQUIRED 事务原子保存订单版本与法律接受，提交后才由 Processor 调用 Payment。 */
    @Override
    public void saveSubmissionContext(SubmitCustomerPaymentContext context) {
        transactions.write(() -> {
            var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            context.getOrder().checkExpiry(now);
            orderRepository.recordPaymentSubmission(context.getOrder(), context.getSession(),
                context.getContract().getConsumerContractId(), context.getPayment(), now);
            if (context.hasNewAcceptances()) {
                evidenceRepository.insert(context.getEvidence());
                for (var acceptance : context.getAcceptances()) {
                    acceptanceRepository.insert(acceptance);
                }
            }
            return null;
        });
    }

    /** Intent 关联与订单 CAS 同事务；取消、过期或版本冲突时不留下部分本地结果。 */
    @Override
    public void bindPayment(SubmitCustomerPaymentContext context) {
        try {
            transactions.write(() -> {
                paymentRepository.insert(context.getPayment());
                orderRepository.recordPaymentSubmission(context.getOrder(), context.getSession(),
                    context.getContract().getConsumerContractId(), context.getPayment(),
                    clock.instant().truncatedTo(ChronoUnit.MICROS));
                return null;
            });
        } catch (DuplicateKeyException conflict) {
            throw new ConcurrentRequestException("订单的 Intent 关联已由另一请求保存，请重新查询", conflict);
        }
    }
}
