package com.paytech.payment.card.service.impl;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.payment.card.core.configuration.PaymentCardProperties;
import com.paytech.payment.card.domain.CardInstrumentContext;
import com.paytech.payment.card.service.CardService;

/** Card 本地合成替身，仅接受文档指定的测试卡 */
@Service
@RequiredArgsConstructor
public class CardServiceImpl implements CardService {
    private final Clock clock;
    private final PaymentCardProperties properties;

    /** 本地生成合成卡引用，真实卡保存应参与调用方事务 */
    @Override
    public void applyInstrument(CardInstrumentContext context) {
        requireMockEnabled();
        if (!isMockCard(context)) {
            throw new BizCheckFailException("Card Mock 仅支持指定的合成卡和 CARD_TEST 支付方式");
        }
        var cardExpiresAt = YearMonth.of(2030, 12).plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC)
            .toInstant().minus(1, ChronoUnit.MICROS);
        if (!cardExpiresAt.isAfter(clock.instant())) {
            throw new BizCheckFailException("合成卡已过期");
        }
        // TODO Card：保存已准备的加密卡记录；KMS 等外部准备由 Card Processor 在事务外完成。
        context.setInstrumentRef(mockReference(context));
        if (context.getExpiresAt().isAfter(cardExpiresAt)) {
            context.setExpiresAt(cardExpiresAt);
        }
    }

    /** 本地核对原合成卡身份，不续期或准备新材料 */
    @Override
    public void checkInstrument(CardInstrumentContext context) {
        requireMockEnabled();
        // TODO Card：按原引用读取卡身份并比较；不比较 CVV，不补材料或续期。
        if (!isMockCard(context) || !Objects.equals(context.getInstrumentRef(), mockReference(context))) {
            throw new ConcurrentRequestException("原提交不可更换卡工具");
        }
    }

    /** 未接入真实 Card 时，必须显式启用合成替身 */
    private void requireMockEnabled() {
        if (!properties.isMockEnabled()) {
            throw new FailException(CommonErrorCode.SERVICE_UNAVAILABLE, "Card 能力尚未接入");
        }
    }

    /** 只识别公开的合成输入，不保存卡输入或计算其摘要 */
    private boolean isMockCard(CardInstrumentContext context) {
        var card = context.getCard();
        return "CARD_TEST".equals(context.getPaymentMethodCode())
            && "4111111111111111".equals(card.getPan())
            && "12".equals(card.getExpiryMonth())
            && "2030".equals(card.getExpiryYear())
            && "MOCK CARD".equals(card.getCardholderName());
    }

    /** 合成引用只由原提交范围构成，不包含卡原值 */
    private String mockReference(CardInstrumentContext context) {
        return "mock-card:" + context.getClientId() + ":" + context.getIntentId() + ":" + context.getSourceSubmissionId();
    }
}
