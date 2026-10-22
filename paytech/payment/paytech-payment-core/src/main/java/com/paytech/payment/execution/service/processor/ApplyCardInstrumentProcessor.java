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
import com.paytech.payment.execution.core.configuration.PaymentExecutionProperties;
import com.paytech.payment.execution.core.constant.Locks;
import com.paytech.payment.execution.core.dal.dataobject.PaymentInstrumentToken;
import com.paytech.payment.execution.core.dal.dataobject.PaymentIntent;
import com.paytech.payment.execution.core.enums.InstrumentType;
import com.paytech.payment.execution.core.enums.IntentStatus;
import com.paytech.payment.execution.domain.ApplyCardInstrumentContext;
import com.paytech.payment.execution.domain.CardInstrumentCommand;
import com.paytech.payment.execution.domain.CardInstrumentResult;
import com.paytech.payment.execution.integration.PaymentCardClient;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentInstrumentTokenRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;
import com.paytech.payment.execution.service.PaymentExecutionService;
import com.paytech.payment.execution.service.processor.request.ApplyCardInstrumentRequest;
import com.paytech.payment.execution.service.processor.response.ApplyCardInstrumentResponse;

import lombok.RequiredArgsConstructor;

/**
 * 申请卡支付工具（design/05 §2.2 两段创建）：先由 card-service 在自己的库内保存卡、凭证与交易工具，再在本地事务内按 Intent 状态保存支付工具 Token。
 *
 * <p>
 * 原提交重放：查到原 Token 后以同一来源提交再调 card，card 核对同卡并返回原工具（不访问 Vault、不覆盖 CVV），工具引用须与原 Token 一致。
 * 新申请：校验资格 → 事务外调 card 创建 → 绑定工具引用、期限、风控属性、BIN 库卡属性快照、Customer 与保存意愿 → 本地事务插入 Token（条件插入依赖 Intent 状态）。
 * 卡属性快照只在首次绑定时冻结；重放沿用原 Token，即使 BIN 库已切换版本也不改写。
 * 第二段失败时 card 侧留下的孤立工具由 card 的到期回收任务处理。
 */
@Component
@RequiredArgsConstructor
public class ApplyCardInstrumentProcessor
    extends AbstractLockProcessor<ApplyCardInstrumentRequest, ApplyCardInstrumentResponse> {
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final Clock clock;
    private final PaymentExecutionProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final PaymentCardClient paymentCardClient;
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

    /** 查询原工具，并由 card 在事务外核对卡身份（同一来源提交重放返回原工具） */
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
        CardInstrumentResult card = paymentCardClient.createInstrument(buildCardCommand(request, token));
        if (!Objects.equals(card.instrumentRef(), token.getInstrumentRef())) {
            throw new ConcurrentRequestException("原提交不可更换卡工具");
        }
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
        token.setCustomerId(request.getCustomerId());
        token.setConsentToSave(request.isSaveCard());
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
     * 两段创建：事务外由 card 创建卡工具，再在本地事务保存 Token
     * <li>card 访问 Vault 与 Redis，必须在事务前完成；创建失败则不保存、不返回 Token。</li>
     * <li>本地事务失败（如 Intent 状态已变化）时 card 侧工具成为孤立工具，CVV 到期删除，由 card 回收任务清理。</li>
     */
    void saveContext(ApplyCardInstrumentContext context, ApplyCardInstrumentRequest request) {
        PaymentInstrumentToken token = context.getInstrumentToken();
        CardInstrumentResult card = paymentCardClient.createInstrument(buildCardCommand(request, token));
        bindCardInstrument(token, card);
        transactionTemplate.executeWithoutResult(status -> paymentExecutionService.saveApplyCardInstrumentContext(context));
    }

    /** 将 card 返回的引用、期限、风控属性与卡属性快照绑定到待保存 Token */
    void bindCardInstrument(PaymentInstrumentToken token, CardInstrumentResult card) {
        if (card.instrumentRef().length() > 128 || card.expiresAt().isAfter(token.getExpiresAt())) {
            throw new ErrorException("Card 返回的工具引用或期限无效");
        }
        token.setInstrumentRef(card.instrumentRef());
        token.setExpiresAt(card.expiresAt().truncatedTo(ChronoUnit.MICROS));
        token.setCardBin(card.cardBin());
        token.setCardFingerprint(card.cardFingerprint());
        bindCardAttributes(token, card.cardAttributes());
    }

    /** 首次绑定时冻结 BIN 库卡属性快照；原提交重放沿用已存 Token，不再改写（B4）。 */
    void bindCardAttributes(PaymentInstrumentToken token, CardInstrumentResult.CardAttributes attributes) {
        token.setCardBrand(attributes.cardBrand());
        token.setFundingType(attributes.fundingType());
        token.setCardLevel(attributes.cardLevel());
        token.setCardProduct(attributes.cardProduct());
        token.setIssuerCountry(attributes.issuerCountry());
        token.setIssuerRegion(attributes.issuerRegion());
        token.setBinLibraryVersionNo(attributes.binLibraryVersionNo());
        token.setBinMatchLength(attributes.matchedPrefixLength());
    }

    /** card 只接收自己需要的参数；重放时期限上限取原 Token 期限 */
    CardInstrumentCommand buildCardCommand(ApplyCardInstrumentRequest request, PaymentInstrumentToken token) {
        CardInstrumentCommand command = new CardInstrumentCommand();
        command.setClientId(token.getClientId());
        command.setCustomerId(request.getCustomerId());
        command.setSourceSubmissionId(token.getSourceSubmissionId());
        command.setCard(request.getCard());
        command.setStoredCardToken(request.getStoredCardToken());
        command.setConsentToSave(request.isSaveCard());
        command.setExpiresAt(token.getExpiresAt());
        return command;
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
