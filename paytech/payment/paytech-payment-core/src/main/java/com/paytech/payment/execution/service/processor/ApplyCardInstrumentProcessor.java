package com.paytech.payment.execution.service.processor;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.*;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.payment.card.domain.CardInstrumentContext;
import com.paytech.payment.card.service.CardService;
import com.paytech.payment.card.service.processor.CheckCardInstrumentProcessor;
import com.paytech.payment.card.service.processor.PrepareCardInstrumentProcessor;
import com.paytech.payment.execution.core.configuration.PaymentExecutionProperties;
import com.paytech.payment.execution.core.constant.Locks;
import com.paytech.payment.execution.core.dal.dataobject.PaymentInstrumentToken;
import com.paytech.payment.execution.core.dal.dataobject.PaymentIntent;
import com.paytech.payment.execution.core.enums.InstrumentType;
import com.paytech.payment.execution.core.enums.IntentStatus;
import com.paytech.payment.execution.domain.ApplyCardInstrumentContext;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentInstrumentTokenRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;
import com.paytech.payment.execution.service.PaymentExecutionService;
import com.paytech.payment.execution.service.processor.request.ApplyCardInstrumentRequest;
import com.paytech.payment.execution.service.processor.response.ApplyCardInstrumentResponse;

import lombok.RequiredArgsConstructor;

/** 申请卡支付工具 */
@Component
@RequiredArgsConstructor
public class ApplyCardInstrumentProcessor
    extends AbstractLockProcessor<ApplyCardInstrumentRequest, ApplyCardInstrumentResponse> {
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final Clock clock;
    private final PaymentExecutionProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final CardService cardService;
    private final PrepareCardInstrumentProcessor prepareCardInstrumentProcessor;
    private final CheckCardInstrumentProcessor checkCardInstrumentProcessor;
    private final PaymentExecutionService paymentExecutionService;
    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentInstrumentTokenRepository paymentInstrumentTokenRepository;

    /** 等待获取 Intent 资源锁。 */
    @Override
    protected LockCommand lockCommand(ApplyCardInstrumentRequest request) {
        return LockCommand.lock(Locks.getIntentLock(request.getIntentId()));
    }

    /** 查询原提交，首次申请统一保存卡工具和 Token */
    @Override
    protected ApplyCardInstrumentResponse processWithinLock(ApplyCardInstrumentRequest request) {
        // 查询原提交
        PaymentIntent intent = getAndCheckPaymentIntent(request);
        ApplyCardInstrumentContext context = checkAndGetContext(intent, request);
        if (context != null) {
            return processResponse(context);
        }

        // 构建上下文
        context = buildApplyCardInstrumentContext(intent, request);

        // 保存卡工具和 Token
        saveContext(context, request);

        // 响应处理
        return processResponse(context);
    }

    /** 查询并检查 Intent 归属 */
    PaymentIntent getAndCheckPaymentIntent(ApplyCardInstrumentRequest request) {
        PaymentIntent intent = paymentIntentRepository.findIntent(request.getIntentId());
        if (intent == null || !Objects.equals(intent.getClientId(), request.getClientId())) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "支付意图不存在");
        }
        return intent;
    }

    /** 查询原工具，并由 Card 在事务外核对卡身份 */
    ApplyCardInstrumentContext checkAndGetContext(PaymentIntent intent, ApplyCardInstrumentRequest request) {
        PaymentInstrumentToken token = paymentInstrumentTokenRepository.findTokenBySubmission(intent.getClientId(),
            intent.getPaymentIntentId(), request.getSourceSubmissionId());
        if (token == null) {
            return null;
        }
        if (token.getInstrumentType() != InstrumentType.CARD
            || !Objects.equals(token.getPaymentMethodCode(), request.getPaymentMethodCode())) {
            throw new ConcurrentRequestException("原提交不可更换支付方式或工具类型");
        }
        checkCardInstrumentProcessor.process(buildCardContext(request, token));
        return new ApplyCardInstrumentContext(intent, token);
    }

    /** 校验新申请并构建上下文 */
    ApplyCardInstrumentContext buildApplyCardInstrumentContext(PaymentIntent intent,
        ApplyCardInstrumentRequest request) {
        checkNewApplicationAllowed(intent, request);
        Duration preparationWindow = properties.getInstrumentPreparationWindow();
        if (preparationWindow == null || preparationWindow.isZero() || preparationWindow.isNegative()) {
            throw new FailException(CommonErrorCode.SERVICE_UNAVAILABLE, "支付工具申请窗口尚未配置");
        }
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Duration remaining = Duration.between(now, intent.getExpiresAt());
        Duration window = preparationWindow.compareTo(remaining) < 0 ? preparationWindow : remaining;

        PaymentInstrumentToken token = new PaymentInstrumentToken();
        token.setPaymentInstrumentToken(generateToken());
        token.setClientId(intent.getClientId());
        token.setPaymentIntentId(intent.getPaymentIntentId());
        token.setSourceSubmissionId(request.getSourceSubmissionId());
        token.setPaymentMethodCode(request.getPaymentMethodCode());
        token.setInstrumentType(InstrumentType.CARD);
        token.setExpiresAt(now.plus(window).truncatedTo(ChronoUnit.MICROS));
        token.setCreateTime(now);
        token.setUpdateTime(now);
        return new ApplyCardInstrumentContext(intent, token);
    }

    /** 检查首次申请资格 */
    void checkNewApplicationAllowed(PaymentIntent intent, ApplyCardInstrumentRequest request) {
        if (!intent.statusIn(IntentStatus.OPEN) || !intent.getExpiresAt().isAfter(clock.instant())) {
            throw new BizCheckFailException("支付意图已关闭或过期");
        }
        if (intent.hasActiveAttempt() || intent.getSuccessfulAttemptId() != null) {
            throw new BizCheckFailException("支付意图已有进行中或成功的支付尝试");
        }
        if (paymentAttemptRepository.findAttemptBySubmission(request.getSourceSubmissionId()) != null) {
            throw new ConcurrentRequestException("来源提交已用于支付，不能重新申请工具");
        }
    }

    /**
     * 事务外由 Card 准备卡记录，再在同一事务调用 Card 和 Execution 的本地保存能力
     * <li>Card 准备访问 Vault 与 Redis，必须在事务前完成；准备失败则不保存、不返回 Token。</li>
     * <li>事务失败时 Card 准备留下的 CVV 到期删除，Vault 中的 DEK 作为无引用残留保留。</li>
     */
    void saveContext(ApplyCardInstrumentContext context, ApplyCardInstrumentRequest request) {
        PaymentInstrumentToken token = context.getInstrumentToken();
        CardInstrumentContext cardContext = prepareCardInstrumentProcessor.process(buildCardContext(request, token));
        transactionTemplate.executeWithoutResult(status -> {
            cardService.applyInstrument(cardContext);
            bindCardInstrument(token, cardContext);
            paymentExecutionService.saveApplyCardInstrumentContext(context);
        });
    }

    /** 将 Card 返回的引用和期限绑定到待保存 Token */
    void bindCardInstrument(PaymentInstrumentToken token, CardInstrumentContext cardContext) {
        if (cardContext.getInstrumentRef() == null || cardContext.getInstrumentRef().isBlank()
            || cardContext.getInstrumentRef().length() > 128 || cardContext.getExpiresAt() == null
            || cardContext.getExpiresAt().isAfter(token.getExpiresAt())) {
            throw new ErrorException("Card 返回的工具引用或期限无效");
        }
        token.setInstrumentRef(cardContext.getInstrumentRef());
        token.setExpiresAt(cardContext.getExpiresAt().truncatedTo(ChronoUnit.MICROS));
    }

    /** Card 只接收自己的领域参数 */
    CardInstrumentContext buildCardContext(ApplyCardInstrumentRequest request, PaymentInstrumentToken token) {
        CardInstrumentContext cardContext = new CardInstrumentContext(token.getClientId(), token.getPaymentIntentId(),
            token.getSourceSubmissionId(), token.getPaymentMethodCode(), request.getCard(), token.getInstrumentRef(),
            token.getExpiresAt());
        cardContext.setStoredCardToken(request.getStoredCardToken());
        cardContext.setCustomerId(request.getCustomerId());
        cardContext.setSaveCard(request.isSaveCard());
        return cardContext;
    }

    /** 生成不包含业务身份的随机 Token */
    String generateToken() {
        byte[] bytes = new byte[32];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 响应处理 */
    ApplyCardInstrumentResponse processResponse(ApplyCardInstrumentContext context) {
        ApplyCardInstrumentResponse response = new ApplyCardInstrumentResponse();
        response.setPaymentInstrumentToken(context.getInstrumentToken().getPaymentInstrumentToken());
        response.setExpiresAt(context.getInstrumentToken().getExpiresAt());
        return response;
    }
}
