package com.paytech.payment.execution.service.processor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAttempt;
import com.paytech.payment.execution.core.dal.dataobject.PaymentIntent;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.domainservice.repository.PaymentActionRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;
import com.paytech.payment.execution.service.processor.request.GetSubmissionRequest;
import com.paytech.payment.execution.service.processor.response.GetSubmissionResponse;

import lombok.AllArgsConstructor;

/**
 * 按来源提交查询原支付结果（EX-I06）
 * <li>Intent 不存在或跨 Client、该提交没有支付尝试（只申请了工具也算）或尝试属于其他 Intent，统一返回未找到。</li>
 * <li>失败代码与交互取自尝试的最后一个动作；交互只在处理中且未过期时返回，跳转地址不落库也不返回。</li>
 */
@Component
@AllArgsConstructor
public class GetSubmissionProcessor extends AbstractProcessor<GetSubmissionRequest, GetSubmissionResponse> {
    private final Clock clock;
    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentActionRepository paymentActionRepository;

    @Override
    protected GetSubmissionResponse processRequest(GetSubmissionRequest request) {
        // 查询并校验 Intent 归属
        PaymentIntent intent = paymentIntentRepository.findIntent(request.getIntentId());
        if (intent == null || !Objects.equals(intent.getClientId(), request.getClientId())) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "支付意图不存在");
        }

        // 按来源提交查询原尝试
        PaymentAttempt attempt = paymentAttemptRepository.findAttemptBySubmission(request.getSourceSubmissionId());
        if (attempt == null || !Objects.equals(attempt.getPaymentIntentId(), intent.getPaymentIntentId())) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "支付提交不存在");
        }

        // 最后一个动作承载失败代码与交互
        PaymentAction lastAction = paymentActionRepository.findLatestByAttempt(attempt.getPaymentAttemptId());

        // 响应处理
        return processResponse(intent, attempt, lastAction, clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    /** 从同一次读取的 Intent、尝试与最后动作组装响应。 */
    GetSubmissionResponse processResponse(PaymentIntent intent, PaymentAttempt attempt, PaymentAction lastAction,
        Instant now) {
        GetSubmissionResponse response = new GetSubmissionResponse();
        response.setSourceSubmissionId(attempt.getSourceSubmissionId());
        response.setIntentId(intent.getPaymentIntentId());
        response.setAttemptId(attempt.getPaymentAttemptId());
        response.setPaymentMethodCode(attempt.getPaymentMethodCode());
        response.setStatus(attempt.getAttemptStatus());
        response.setCapturedAmount(Objects.requireNonNullElse(attempt.getCapturedAmount(), BigDecimal.ZERO));
        response.setCurrency(intent.getCurrency());
        response.setObservedAt(now);
        if (lastAction == null) {
            return response;
        }
        if (attempt.getAttemptStatus() == AttemptStatus.FAILED) {
            response.setFailureCode(lastAction.getChannelResponseCode());
        }
        if (attempt.getAttemptStatus() == AttemptStatus.PROCESSING && hasOpenInteraction(lastAction, now)) {
            response.setInteractionType(lastAction.getInteractionType());
            response.setInteractionExpiresAt(lastAction.getInteractionExpiresAt());
        }
        return response;
    }

    /** 动作记有交互类型且截止时间尚未到达。 */
    static boolean hasOpenInteraction(PaymentAction action, Instant now) {
        return action.getInteractionType() != null && action.getInteractionExpiresAt() != null
            && action.getInteractionExpiresAt().isAfter(now);
    }
}
