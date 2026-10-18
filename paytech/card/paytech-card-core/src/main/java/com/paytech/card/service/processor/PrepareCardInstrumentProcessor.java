package com.paytech.card.service.processor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.card.core.crypto.CardCipher;
import com.paytech.card.core.dal.dataobject.PaymentCard;
import com.paytech.card.core.dal.dataobject.PaymentCardCredential;
import com.paytech.card.core.dal.dataobject.PaymentStoredCard;
import com.paytech.card.core.enums.StoredCardStatus;
import com.paytech.card.domain.CardInstrumentContext;
import com.paytech.card.domain.CardInstrumentContext.CardInput;
import com.paytech.card.domain.PreparedInstrument;
import com.paytech.card.domainservice.repository.PaymentCardCredentialRepository;
import com.paytech.card.domainservice.repository.PaymentCardRepository;
import com.paytech.card.domainservice.repository.PaymentStoredCardRepository;
import com.paytech.card.integration.CardKeyClient;
import com.paytech.card.integration.CardSecurityCodeClient;
import com.paytech.card.service.CardStorageService;

import lombok.AllArgsConstructor;

/**
 * 新卡工具的事务外准备（Payment Card 加密存储技术方案第 3 版第 2.2 节）。
 *
 * <p>
 * 校验卡号与有效期 → 按卡号查卡，已有则从 Vault 取回 DEK，没有则生成 DEK、交 Vault 托管、加密卡号并保存 → 按（卡、凭证摘要）查凭证，没有则加密有效期与持卡人原值并保存 → 分配交易工具 ID，用 DEK 加密 CVV 存
 * Redis。卡与凭证在此单条插入、自动提交，主事务失败时保留供复用；交易工具随后由 {@link CreateCardInstrumentProcessor} 交 {@code CardService.applyInstrument} 单条保存。并发首用同一卡号时输的一方改用已有卡，本次托管的 DEK
 * 成为无引用残留。任一步失败即申请失败。DEK 明文用完清零。
 *
 * <p>
 * 用已存卡（Customer 与存卡支付技术方案 §2.4）：按 Token 取已存卡，核对 Client、Customer 与状态，用其当前已验证凭证的有效期限制工具期限；取回 DEK 加密本次 CVV，不新建卡与凭证。
 * 同意保存只对手输卡生效，且必须有 Customer；交易工具记下 Customer、是否保存与已存卡，供 Card 消费 PaymentSucceeded 时存卡。
 */
@Component
@AllArgsConstructor
public class PrepareCardInstrumentProcessor extends AbstractProcessor<CardInstrumentContext, CardInstrumentContext> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final CardCipher cardCipher;
    private final CardKeyClient cardKeyClient;
    private final CardSecurityCodeClient cardSecurityCodeClient;
    private final CardStorageService cardStorageService;
    private final PaymentCardRepository paymentCardRepository;
    private final PaymentCardCredentialRepository paymentCardCredentialRepository;
    private final PaymentStoredCardRepository paymentStoredCardRepository;

    /** 新申请必须带卡输入与期限上限，且尚未分配工具引用；保存与已存卡都要求 Customer。 */
    @Override
    protected void validate(CardInstrumentContext context) {
        if (context.getCard() == null || context.getExpiresAt() == null || context.getInstrumentRef() != null) {
            throw new ErrorException("新卡工具准备的上下文无效");
        }
        if ((context.isSaveCard() || context.getStoredCardToken() != null) && context.getCustomerId() == null) {
            throw new BizCheckFailException("保存卡或使用已存卡必须关联 Customer");
        }
        if (context.isSaveCard() && context.getStoredCardToken() != null) {
            throw new BizCheckFailException("使用已存卡时不能再次选择保存");
        }
    }

    @Override
    protected CardInstrumentContext processRequest(CardInstrumentContext context) {
        if (context.getStoredCardToken() != null) {
            return prepareStoredCard(context);
        }
        CardInput card = context.getCard();
        checkPan(card.getPan());
        Duration ttl = limitExpiry(context, cardExpiresAt(card.getExpiryMonth(), card.getExpiryYear()));
        UnlockedCard unlocked = resolveCard(card.getPan());
        try {
            PaymentCardCredential credential = resolveCredential(unlocked, card);
            prepareInstrument(context, unlocked.dataKey(), credential.getPaymentCardCredentialId(), null, ttl);
            return context;
        } finally {
            Arrays.fill(unlocked.dataKey(), (byte)0);
        }
    }

    /** 用已存卡：核对归属与状态，以当前凭证有效期限制期限，取回 DEK 加密本次 CVV。 */
    CardInstrumentContext prepareStoredCard(CardInstrumentContext context) {
        PaymentStoredCard storedCard = paymentStoredCardRepository.findByToken(context.getStoredCardToken());
        if (storedCard == null || storedCard.getStoredCardStatus() != StoredCardStatus.ACTIVE
            || !storedCard.getClientId().equals(context.getClientId())
            || !storedCard.getCustomerId().equals(context.getCustomerId())) {
            throw new BizCheckFailException("已存卡不存在或不可用");
        }
        PaymentCardCredential credential =
            paymentCardCredentialRepository.findCredential(storedCard.getPaymentCardCredentialId());
        PaymentCard card = paymentCardRepository.findCard(storedCard.getPaymentCardId());
        if (credential == null || card == null) {
            throw new ErrorException("已存卡关联的卡或凭证不存在: " + storedCard.getPaymentStoredCardId());
        }
        Duration ttl = limitExpiry(context, cardExpiresAt(credential.getExpiryMonth(), credential.getExpiryYear()));
        byte[] dataKey = cardKeyClient.reveal(card.getDekVaultToken());
        try {
            prepareInstrument(context, dataKey, credential.getPaymentCardCredentialId(),
                storedCard.getPaymentStoredCardId(), ttl);
            return context;
        } finally {
            Arrays.fill(dataKey, (byte)0);
        }
    }

    /** 分配交易工具 ID，加密 CVV 暂存 Redis，并把待保存的交易工具写回上下文。 */
    void prepareInstrument(CardInstrumentContext context, byte[] dataKey, long paymentCardCredentialId,
        Long paymentStoredCardId, Duration ttl) {
        long paymentCardInstrumentId = idGenerator.nextId();
        cardSecurityCodeClient.save(paymentCardInstrumentId,
            cardCipher.encryptSecurityCode(dataKey, paymentCardInstrumentId, context.getCard().getCvv()), ttl);
        context.setPreparedInstrument(
            new PreparedInstrument(paymentCardInstrumentId, paymentCardCredentialId, paymentStoredCardId));
        context.setInstrumentRef(String.valueOf(paymentCardInstrumentId));
    }

    /**
     * 工具期限不超过卡有效期，写回上下文
     *
     * @return CVV 暂存时长，即工具剩余期限
     */
    Duration limitExpiry(CardInstrumentContext context, Instant cardExpiresAt) {
        Instant expiresAt = context.getExpiresAt().isAfter(cardExpiresAt) ? cardExpiresAt : context.getExpiresAt();
        Duration ttl = Duration.between(clock.instant(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            throw new BizCheckFailException("支付工具期限已到");
        }
        context.setExpiresAt(expiresAt);
        return ttl;
    }

    /** 校验卡号格式与 Luhn。 */
    static void checkPan(String pan) {
        if (pan == null || !pan.matches("[0-9]{12,19}") || !luhnValid(pan)) {
            throw new BizCheckFailException("卡号校验失败");
        }
    }

    /**
     * 卡有效期截止时间（有效期月份最后一微秒，UTC）；已过期时拒绝
     */
    Instant cardExpiresAt(String expiryMonth, String expiryYear) {
        Instant cardExpiresAt = YearMonth.of(Integer.parseInt(expiryYear), Integer.parseInt(expiryMonth)).plusMonths(1)
            .atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().minus(1, ChronoUnit.MICROS);
        if (!cardExpiresAt.isAfter(clock.instant())) {
            throw new BizCheckFailException("卡已过期");
        }
        return cardExpiresAt;
    }

    /**
     * 按卡号取得卡及其 DEK：已有卡从 Vault 取回 DEK；新卡生成 DEK 并保存，撞唯一键则改用已有卡
     *
     * @return 卡与 DEK，调用方用完清零 DEK
     */
    UnlockedCard resolveCard(String pan) {
        PaymentCard existing = cardStorageService.findCard(pan);
        if (existing != null) {
            return new UnlockedCard(existing, cardKeyClient.reveal(existing.getDekVaultToken()));
        }
        byte[] dataKey = cardCipher.newDataKey();
        PaymentCard created;
        PaymentCard saved;
        try {
            created = newCard(pan, dataKey);
            saved = cardStorageService.saveCard(created);
        } catch (RuntimeException failed) {
            Arrays.fill(dataKey, (byte)0);
            throw failed;
        }
        if (saved == created) {
            return new UnlockedCard(saved, dataKey);
        }
        Arrays.fill(dataKey, (byte)0);
        return new UnlockedCard(saved, cardKeyClient.reveal(saved.getDekVaultToken()));
    }

    /** 新卡：分配 ID，DEK 交 Vault 托管，加密卡号，填 BIN、后四位与指纹。 */
    PaymentCard newCard(String pan, byte[] dataKey) {
        long paymentCardId = idGenerator.nextId();
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        PaymentCard card = new PaymentCard();
        card.setPaymentCardId(paymentCardId);
        card.setCardFingerprint(cardCipher.fingerprint(pan));
        card.setCardBin(CardCipher.bin(pan));
        card.setPanLast4(CardCipher.last4(pan));
        card.setDekVaultToken(cardKeyClient.protect(paymentCardId, dataKey));
        card.setEncryptedPan(cardCipher.encryptPan(dataKey, paymentCardId, pan));
        card.setCreateTime(now);
        card.setUpdateTime(now);
        return card;
    }

    /** 按（卡、凭证摘要）取得凭证，没有则加密原值后保存。 */
    PaymentCardCredential resolveCredential(UnlockedCard unlocked, CardInput card) {
        long paymentCardId = unlocked.card().getPaymentCardId();
        String digest = cardCipher.credentialDigest(card);
        PaymentCardCredential existing = paymentCardCredentialRepository.findCredentialByDigest(paymentCardId, digest);
        if (existing != null) {
            return existing;
        }
        long paymentCardCredentialId = idGenerator.nextId();
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        PaymentCardCredential credential = new PaymentCardCredential();
        credential.setPaymentCardCredentialId(paymentCardCredentialId);
        credential.setPaymentCardId(paymentCardId);
        credential.setCredentialDigest(digest);
        credential.setEncryptedDetails(cardCipher.encryptDetails(unlocked.dataKey(), paymentCardCredentialId, card));
        credential.setExpiryMonth(card.getExpiryMonth());
        credential.setExpiryYear(card.getExpiryYear());
        credential.setCreateTime(now);
        credential.setUpdateTime(now);
        return cardStorageService.saveCredential(credential);
    }

    /** Luhn 校验位。 */
    static boolean luhnValid(String pan) {
        int sum = 0;
        boolean doubled = false;
        for (int i = pan.length() - 1; i >= 0; i--) {
            int digit = pan.charAt(i) - '0';
            if (doubled) {
                digit = digit * 2 > 9 ? digit * 2 - 9 : digit * 2;
            }
            sum += digit;
            doubled = !doubled;
        }
        return sum % 10 == 0;
    }

    /** 卡与解开的 DEK。 */
    record UnlockedCard(PaymentCard card, byte[] dataKey) {}
}
