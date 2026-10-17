package com.paytech.payment.execution.service.processor;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.payment.execution.core.constant.Locks;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAttempt;
import com.paytech.payment.execution.core.dal.dataobject.PaymentIntent;
import com.paytech.payment.execution.core.enums.TerminationDecision;
import com.paytech.payment.execution.domain.TerminateIntentContext;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;
import com.paytech.payment.execution.service.PaymentExecutionService;
import com.paytech.payment.execution.service.processor.request.TerminateIntentRequest;
import com.paytech.payment.execution.service.processor.response.TerminateIntentResponse;

import lombok.AllArgsConstructor;

/** 终止支付意图 */
@Component
@AllArgsConstructor
public class TerminateIntentProcessor extends AbstractLockProcessor<TerminateIntentRequest, TerminateIntentResponse> {
    private final PaymentExecutionService paymentExecutionService;
    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;

    /** 与 pay 共用 Intent 资源锁，竞争时返回并发冲突。 */
    @Override
    protected LockCommand lockCommand(TerminateIntentRequest request) {
        return LockCommand.tryLock(Locks.getIntentLock(request.getIntentId()));
    }

    /** 检查当前事实，仅在允许终止时保存。 */
    @Override
    protected TerminateIntentResponse processWithinLock(TerminateIntentRequest request) {
        // 构建上下文
        TerminateIntentContext context = buildContext(request);

        // 判断终止条件
        TerminationDecision decision = context.terminationDecision();
        if (decision != TerminationDecision.TERMINATED) {
            return processResponse(context, decision);
        }

        // 保存上下文
        paymentExecutionService.terminateIntent(context);

        // 响应处理
        return processResponse(context, decision);
    }

    /** 查询归属和活动尝试，缺失的关联记录不能解释为无支付。 */
    TerminateIntentContext buildContext(TerminateIntentRequest request) {
        PaymentIntent intent = paymentIntentRepository.findIntent(request.getIntentId());
        if (intent == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "支付意图不存在");
        }
        if (!Objects.equals(intent.getClientId(), request.getClientId())) {
            throw new BizCheckFailException("支付意图不属于当前 Client");
        }
        PaymentAttempt attempt = null;
        if (intent.hasActiveAttempt()) {
            attempt = paymentAttemptRepository.selectById(intent.getActiveAttemptId());
            if (attempt == null || !Objects.equals(attempt.getPaymentIntentId(), intent.getPaymentIntentId())) {
                throw new ErrorException("支付意图关联的活动尝试不存在或不匹配");
            }
        }
        return new TerminateIntentContext(intent, attempt);
    }

    /** 使用当前上下文组装响应。 */
    TerminateIntentResponse processResponse(TerminateIntentContext context, TerminationDecision decision) {
        TerminateIntentResponse response = new TerminateIntentResponse();
        response.setIntentId(context.getIntent().getPaymentIntentId());
        response.setStatus(context.getIntent().getIntentStatus());
        response.setDecision(decision);
        return response;
    }
}
