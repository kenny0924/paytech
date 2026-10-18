package com.paytech.card.service.handler;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.card.core.dal.dataobject.PaymentCardInstrument;
import com.paytech.card.domain.CardInstrumentRecord;
import com.paytech.card.service.CardService;
import com.paytech.card.service.CardStorageService;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventConsumer;
import com.paytech.foundation.messaging.api.DomainEventHandler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * 卡支付成功后的凭证验证与存卡（design/05 §2.3）：消费 payment.events 的 PaymentSucceeded（分区队列 card.payment.p0..p3）。
 *
 * <p>
 * 由 paytech-messaging 的消费拦截器在本地事务内调用，已按事件 ID（每个支付尝试的成功事件唯一）去重；本 Handler 再以 Card 自己的表做业务幂等：
 * 凭证已验证时不改，同一 Customer 同一张卡的已存卡已存在时只更新凭证与同意时间，因此同一 paymentAttemptId 重复投递只会有一张已存卡。
 * <li>Schema v1（第 12 步前的事件）不含工具引用，那时卡表已在支付成功事务内写入，直接确认跳过；v2 无 instrumentRef 为非卡支付，跳过。</li>
 * <li>Payload 中的 customerId、consentToSave 必须与 Card 工具记录一致，不一致视为数据错误抛出（回滚并重投，超过上限进入 DLQ），不按任一方猜测存卡。</li>
 */
@Slf4j
@Component
@AllArgsConstructor
@DomainEventHandler(eventType = PaymentSucceededCardHandler.EVENT_TYPE, strictOrder = false)
public class PaymentSucceededCardHandler implements DomainEventConsumer {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "PaymentSucceeded";

    /** 携带卡工具字段的最低 Schema 版本。 */
    static final int CARD_FIELDS_SCHEMA_VERSION = 2;

    private final CardService cardService;
    private final CardStorageService cardStorageService;

    @Override
    public void consume(DomainEvent event) {
        if (event.schemaVersion() < CARD_FIELDS_SCHEMA_VERSION) {
            log.info("PaymentSucceeded 早于 Schema v2，卡侧已在原支付事务内处理: eventId={}", event.eventId());
            return;
        }
        JsonNode payload = event.payload();
        JsonNode instrumentRef = payload.path("instrumentRef");
        if (!instrumentRef.isString()) {
            return;
        }
        CardInstrumentRecord record = cardStorageService.findInstrument(instrumentRef.asString());
        if (record == null) {
            throw new ErrorException("支付成功的卡交易工具不存在: eventId=" + event.eventId());
        }
        checkConsistent(record.instrument(), payload, event.eventId());
        cardService.paymentSucceeded(instrumentRef.asString());
    }

    /** 事件中的 Customer 与保存意愿须与工具记录一致。 */
    static void checkConsistent(PaymentCardInstrument instrument, JsonNode payload, String eventId) {
        JsonNode customer = payload.path("customerId");
        Long customerId = customer.isString() ? Long.valueOf(customer.asString()) : null;
        JsonNode consent = payload.path("consentToSave");
        if (!consent.isBoolean() || !Objects.equals(customerId, instrument.getCustomerId())
            || consent.asBoolean() != instrument.isSaveRequested()) {
            throw new ErrorException("PaymentSucceeded 的 Customer 或保存意愿与卡交易工具不一致: eventId=" + eventId);
        }
    }
}
