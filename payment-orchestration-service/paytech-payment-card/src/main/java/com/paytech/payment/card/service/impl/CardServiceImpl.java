package com.paytech.payment.card.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.payment.card.core.configuration.PaymentCardProperties;
import com.paytech.payment.card.domain.CardInstrumentContext;
import com.paytech.payment.card.domain.ChannelCardMaterial;
import com.paytech.payment.card.service.CardService;

/**
 * Card 本地合成替身，仅接受配置的测试卡号（payment.card.mock-pans）。
 *
 * <p>
 * 合成卡材料只保存在本进程内存（按工具引用，带工具期限），不写库、不记录日志；渠道执行领取时 CVV 一次性清除。 真实 Card 的加密存储与 CVV 暂存介质仍待接入；
 * 未启用 Mock 时全部能力不可用。
 */
@Service
@RequiredArgsConstructor
public class CardServiceImpl implements CardService {
    private final Clock clock;
    private final PaymentCardProperties properties;
    /** 合成卡材料，按工具引用；进程重启即丢失，与真实 CVV 暂存同样不可重建。 */
    private final Map<String, MockMaterial> materials = new ConcurrentHashMap<>();

    /** 本地生成合成卡引用并暂存合成材料，真实卡保存应参与调用方事务 */
    @Override
    public void applyInstrument(CardInstrumentContext context) {
        requireMockEnabled();
        if (!isMockCard(context)) {
            throw new BizCheckFailException("Card Mock 仅支持配置的合成测试卡");
        }
        var cardExpiresAt = cardExpiresAt(context.getCard());
        if (!cardExpiresAt.isAfter(clock.instant())) {
            throw new BizCheckFailException("合成卡已过期");
        }
        // TODO Card：保存已准备的加密卡记录；KMS 等外部准备由 Card Processor 在事务外完成。
        context.setInstrumentRef(mockReference(context));
        if (context.getExpiresAt().isAfter(cardExpiresAt)) {
            context.setExpiresAt(cardExpiresAt);
        }
        materials.put(context.getInstrumentRef(), new MockMaterial(context.getCard(), context.getExpiresAt()));
    }

    /** 本地核对原合成卡身份，不续期或准备新材料 */
    @Override
    public void checkInstrument(CardInstrumentContext context) {
        requireMockEnabled();
        // TODO Card：按原引用读取卡身份并比较；不比较 CVV，不补材料或续期。
        var stored = materials.get(context.getInstrumentRef());
        if (!isMockCard(context) || !Objects.equals(context.getInstrumentRef(), mockReference(context))
            || stored != null && !stored.sameIdentity(context.getCard())) {
            throw new ConcurrentRequestException("原提交不可更换卡工具");
        }
    }

    /** 领取合成材料；CVV 只能领取一次，领取即清除 */
    @Override
    public ChannelCardMaterial claimChannelMaterial(String instrumentRef) {
        requireMockEnabled();
        var stored = materials.get(instrumentRef);
        if (stored == null || !stored.expiresAt().isAfter(clock.instant())) {
            materials.remove(instrumentRef);
            throw new BizCheckFailException("卡材料不存在或已过期");
        }
        String cvv = stored.claimCvv();
        if (cvv == null) {
            throw new BizCheckFailException("卡安全码已领取，不能再次用于渠道执行");
        }
        var card = stored.card();
        return new ChannelCardMaterial(card.getPan(), card.getExpiryMonth(), card.getExpiryYear(),
            card.getCardholderName(), cvv);
    }

    /** 未接入真实 Card 时，必须显式启用合成替身 */
    private void requireMockEnabled() {
        if (!properties.isMockEnabled()) {
            throw new FailException(CommonErrorCode.SERVICE_UNAVAILABLE, "Card 能力尚未接入");
        }
    }

    /** 只识别配置的公开测试卡号，不保存卡输入摘要 */
    private boolean isMockCard(CardInstrumentContext context) {
        var card = context.getCard();
        return card != null && properties.getMockPans().contains(card.getPan()) && card.getCardholderName() != null
            && !card.getCardholderName().isBlank();
    }

    /** 卡有效期月份最后一微秒（UTC） */
    private static Instant cardExpiresAt(CardInstrumentContext.CardInput card) {
        return YearMonth.of(Integer.parseInt(card.getExpiryYear()), Integer.parseInt(card.getExpiryMonth())).plusMonths(1)
            .atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().minus(1, ChronoUnit.MICROS);
    }

    /** 合成引用只由原提交范围构成，不包含卡原值 */
    private String mockReference(CardInstrumentContext context) {
        return "mock-card:" + context.getClientId() + ":" + context.getIntentId() + ":" + context.getSourceSubmissionId();
    }

    /** 内存中的合成材料；CVV 原子清除。 */
    private static final class MockMaterial {
        private final CardInstrumentContext.CardInput card;
        private final Instant expiresAt;
        private final AtomicReference<String> cvv;

        /** 复制卡输入，避免调用方后续修改影响暂存。 */
        MockMaterial(CardInstrumentContext.CardInput input, Instant expiresAt) {
            var copy = new CardInstrumentContext.CardInput();
            copy.setPan(input.getPan());
            copy.setExpiryMonth(input.getExpiryMonth());
            copy.setExpiryYear(input.getExpiryYear());
            copy.setCardholderName(input.getCardholderName());
            this.card = copy;
            this.expiresAt = expiresAt;
            this.cvv = new AtomicReference<>(input.getCvv());
        }

        /** 卡号、有效期与持卡人相同（不比较 CVV）。 */
        boolean sameIdentity(CardInstrumentContext.CardInput other) {
            return other != null && card.getPan().equals(other.getPan()) && card.getExpiryMonth().equals(other.getExpiryMonth())
                && card.getExpiryYear().equals(other.getExpiryYear())
                && card.getCardholderName().equals(other.getCardholderName());
        }

        /** 取出并清除 CVV；已清除时为 null。 */
        String claimCvv() {
            return cvv.getAndSet(null);
        }

        /** 不含 CVV 的卡输入。 */
        CardInstrumentContext.CardInput card() {
            return card;
        }

        /** 材料期限，等于工具期限。 */
        Instant expiresAt() {
            return expiresAt;
        }
    }
}
