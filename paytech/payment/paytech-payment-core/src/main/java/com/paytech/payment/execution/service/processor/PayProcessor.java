package com.paytech.payment.execution.service.processor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.foundation.money.Money;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.payment.execution.core.constant.Locks;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAttempt;
import com.paytech.payment.execution.core.dal.dataobject.PaymentInstrumentToken;
import com.paytech.payment.execution.core.dal.dataobject.PaymentIntent;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.CaptureMode;
import com.paytech.payment.execution.core.enums.IntentStatus;
import com.paytech.payment.execution.core.enums.PaymentErrorCode;
import com.paytech.payment.execution.domain.PayChannelInput;
import com.paytech.payment.execution.domain.PayContext;
import com.paytech.payment.execution.integration.PaymentChannelClient;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentInstrumentTokenRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;
import com.paytech.payment.execution.service.ActionExecutionService;
import com.paytech.payment.execution.service.PaymentExecutionService;
import com.paytech.payment.execution.service.processor.request.PayRequest;
import com.paytech.payment.execution.service.processor.response.PayResponse;
import com.paytech.payment.routing.domain.RouteContext;
import com.paytech.payment.routing.domain.RouteResult;
import com.paytech.payment.routing.service.RouteService;

import lombok.AllArgsConstructor;

/** 支付受理 */
@Component
@AllArgsConstructor
public class PayProcessor extends AbstractLockProcessor<PayRequest, PayResponse> {
    final Clock clock;
    final RouteService routeService;
    final PaymentChannelClient paymentChannelClient;
    final ActionExecutionService actionExecutionService;
    final PaymentExecutionService paymentExecutionService;
    final PaymentIntentRepository paymentIntentRepository;
    final PaymentAttemptRepository paymentAttemptRepository;
    final PaymentInstrumentTokenRepository paymentInstrumentTokenRepository;

    /** 立即尝试获取 Intent 资源锁。 */
    @Override
    protected LockCommand lockCommand(PayRequest request) {
        return LockCommand.tryLock(Locks.getIntentLock(request.getIntentId()));
    }

    /** 受理支付并推进新尝试。 */
    @Override
    protected PayResponse processWithinLock(PayRequest request) {
        // 查询原提交
        PaymentIntent intent = getAndCheckPaymentIntent(request);
        PayContext context = checkAndGetContext(intent, request);
        if (context != null) {
            return processResponse(context);
        }

        // 构建上下文
        context = buildPayContext(intent, request);

        // 支付路由
        route(context);

        // 保存上下文
        paymentExecutionService.savePayContext(context);

        // 执行动作
        actionExecutionService.execute(context);

        // 响应处理
        return processResponse(context);
    }

    /** 检查 & 获取上下文 */
    PayContext checkAndGetContext(PaymentIntent intent, PayRequest request) {
        PaymentAttempt attempt = getAndCheckOriginalAttempt(intent, request);
        if (attempt == null) {
            return null;
        }
        PaymentInstrumentToken token = getAndCheckOriginalToken(attempt, request);
        return new PayContext(intent, attempt, token);
    }

    /**
     * 上下文构建
     *
     * @return 上下文
     */
    PayContext buildPayContext(PaymentIntent intent, PayRequest request) {
        checkNewAttemptAllowed(intent);
        PaymentInstrumentToken token = getAndCheckPaymentInstrumentToken(intent, request);
        var context = new PayContext(intent, buildPaymentAttempt(intent, token, request), token);
        context.setChannelInput(new PayChannelInput(request.getReturnUrl(), request.getConsumerReference(),
            request.getConsumerIpAddress(), request.getConsumerUserAgent()));
        return context;
    }

    /**
     * 选择并绑定渠道
     * <li>仅新 Attempt 选路；候选查询与选择都在本地保存事务之前完成。</li>
     */
    void route(PayContext context) {
        PaymentIntent intent = context.getIntent();
        PaymentAttempt attempt = context.getAttempt();
        RouteContext routeContext = new RouteContext();
        routeContext.setClientId(intent.getClientId());
        routeContext.setEntityNo(intent.getEntityNo());
        routeContext.setAmount(Money.of(intent.getCurrency(), intent.getAmount()));
        routeContext.setPaymentMethodCode(attempt.getPaymentMethodCode());
        routeContext.setCaptureMode(attempt.getCaptureMode().getCode());
        routeContext.setCountryCode(intent.getConsumerCountryCode());
        routeContext.setMccCode(intent.getMccCode());

        RouteResult result = routeService.route(routeContext, paymentChannelClient.queryRouteCandidates(routeContext));
        attempt.setChannelId(result.getChannelId());
        attempt.setChannelAccountId(result.getChannelAccountId());
        attempt.setMidId(result.getMidId());
    }

    /** 获取 & 检查Intent */
    PaymentIntent getAndCheckPaymentIntent(PayRequest request) {
        PaymentIntent intent = paymentIntentRepository.findIntent(request.getIntentId());
        if (intent == null || !Objects.equals(intent.getClientId(), request.getClientId())) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "支付意图不存在");
        }
        return intent;
    }

    /** 按来源提交查询原尝试并校验 Intent 归属。 */
    PaymentAttempt getAndCheckOriginalAttempt(PaymentIntent intent, PayRequest request) {
        PaymentAttempt attempt = paymentAttemptRepository.findAttemptBySubmission(request.getSourceSubmissionId());
        if (attempt == null) {
            return null;
        }
        if (!Objects.equals(attempt.getPaymentIntentId(), intent.getPaymentIntentId())) {
            throw new ConcurrentRequestException("来源提交已被其他支付意图使用");
        }
        return attempt;
    }

    /** 获取 & 检查Token */
    PaymentInstrumentToken getAndCheckOriginalToken(PaymentAttempt attempt, PayRequest request) {
        PayRequest.PayMethod payMethod = request.getPayMethod();
        if (!attempt.samePayMethod(payMethod.getPaymentMethodCode(), request.getCaptureMode())) {
            throw new ConcurrentRequestException("原提交不可更换支付方式或捕获模式");
        }
        PaymentInstrumentToken token =
            paymentInstrumentTokenRepository.findToken(attempt.getPaymentInstrumentTokenId());
        if (!Strings.CS.equals(token.getPaymentInstrumentToken(), payMethod.getPaymentInstrumentToken())) {
            throw new ConcurrentRequestException("原提交不可更换支付工具");
        }
        return token;
    }

    /** 检查新尝试资格 */
    void checkNewAttemptAllowed(PaymentIntent intent) {
        if (!intent.statusIn(IntentStatus.OPEN)) {
            throw new BizCheckFailException("Payment Intent状态非OPEN");
        } else if (!intent.getExpiresAt().isAfter(clock.instant())) {
            throw new BizCheckFailException("Payment Intent已过期");
        } else if (intent.getActiveAttemptId() != null) {
            throw new BizCheckFailException("当前存在进行中的Payment Attempt");
        } else if (intent.getSuccessfulAttemptId() != null) {
            throw new BizCheckFailException("Payment Intent已完成");
        }
    }

    /** 获取 & 检查Token */
    PaymentInstrumentToken getAndCheckPaymentInstrumentToken(PaymentIntent intent, PayRequest request) {
        PaymentInstrumentToken token =
            paymentInstrumentTokenRepository.findTokenByValue(request.getPayMethod().getPaymentInstrumentToken());
        if (token == null
            || !token.getPaymentInstrumentToken().equals(request.getPayMethod().getPaymentInstrumentToken())
            || !Objects.equals(token.getClientId(), intent.getClientId())
            || !Objects.equals(token.getPaymentIntentId(), intent.getPaymentIntentId())
            || !Objects.equals(token.getSourceSubmissionId(), request.getSourceSubmissionId())) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "支付工具不存在");
        }
        if (!token.getPaymentMethodCode().equals(request.getPayMethod().getPaymentMethodCode())) {
            throw new ConcurrentRequestException("支付方式与工具不一致");
        }
        if (token.getRevokedAt() != null) {
            throw new FailException(PaymentErrorCode.PAYMENT_INSTRUMENT_REVOKED);
        }
        if (!token.getExpiresAt().isAfter(clock.instant())) {
            throw new FailException(PaymentErrorCode.PAYMENT_INSTRUMENT_EXPIRED);
        }
        return token;
    }

    /** 构建attempt */
    PaymentAttempt buildPaymentAttempt(PaymentIntent intent, PaymentInstrumentToken token, PayRequest request) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPaymentIntentId(intent.getPaymentIntentId());
        attempt.setAttemptNo(Math.addExact(paymentAttemptRepository.maxAttemptNo(intent.getPaymentIntentId()), 1));
        attempt.setSourceSubmissionId(token.getSourceSubmissionId());
        attempt.setPaymentMethodCode(token.getPaymentMethodCode());
        attempt.setCaptureMode(EnumUtils.getEnumByCode(CaptureMode.class, request.getCaptureMode()));
        attempt.setPaymentInstrumentTokenId(token.getPaymentInstrumentTokenId());
        attempt.setAttemptStatus(AttemptStatus.PROCESSING);
        attempt.setAuthorizedAmount(BigDecimal.ZERO);
        attempt.setCapturedAmount(BigDecimal.ZERO);
        attempt.setVersion(1L);
        attempt.setCreateTime(now);
        attempt.setUpdateTime(now);
        return attempt;
    }

    /** 响应处理 */
    PayResponse processResponse(PayContext context) {
        var attempt = context.getAttempt();
        PayResponse response = new PayResponse();
        response.setAttemptId(attempt.getPaymentAttemptId());
        response.setStatus(attempt.getAttemptStatus());
        response.setNextAction(context.getNextAction());
        return response;
    }
}
