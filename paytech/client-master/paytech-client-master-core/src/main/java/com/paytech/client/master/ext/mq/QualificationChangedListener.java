package com.paytech.client.master.ext.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paytech.client.master.service.processor.ApplyQualificationChangeProcessor;
import com.paytech.client.master.service.processor.request.ApplyQualificationChangeRequest;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.messaging.api.ConsumeTransaction;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventConsumer;
import com.paytech.foundation.messaging.api.DomainEventHandler;
import com.paytech.foundation.messaging.api.DomainEventRejectedException;

import tools.jackson.databind.JsonNode;

/**
 * 消费 compliance QualificationChanged v1（与 ClientReviewDecided 同一 client-master.compliance 分区队列）
 * <li>只处理 CLIENT 角色 REVOKED → Client SUSPENDED；其余事件确认后忽略。</li>
 * <li>信封不合法或业务拒绝进入死信；并发冲突与依赖失败原样抛出重投。</li>
 */
@DomainEventHandler(eventType = QualificationChangedListener.EVENT_TYPE, strictOrder = false,
    transaction = ConsumeTransaction.HANDLER_MANAGED)
public class QualificationChangedListener implements DomainEventConsumer {
    /** 事件类型。 */
    static final String EVENT_TYPE = "QualificationChanged";

    private static final Logger LOG = LoggerFactory.getLogger(QualificationChangedListener.class);

    private final ApplyQualificationChangeProcessor processor;

    /** 只依赖资格变化落地用例。 */
    public QualificationChangedListener(ApplyQualificationChangeProcessor processor) {
        this.processor = processor;
    }

    /** 日志只记录事件 ID，不记录正文。 */
    @Override
    public void consume(DomainEvent event) {
        try {
            var result = processor.process(convert(event));
            LOG.info("Qualification change {} handled: {}", event.eventId(), result);
        } catch (ConcurrentRequestException conflict) {
            LOG.warn("Qualification change {} conflicted and will be redelivered", event.eventId());
            throw conflict;
        } catch (IllegalArgumentException | FailException rejected) {
            LOG.warn("Qualification change {} rejected to dead letter", event.eventId());
            throw new DomainEventRejectedException("资格变化事件被拒绝: " + event.eventId(), rejected);
        }
    }

    /** 校验信封身份并转换；Payload 字段见 compliance QualificationChangedPayload。 */
    static ApplyQualificationChangeRequest convert(DomainEvent event) {
        JsonNode payload = event.payload();
        if (!EVENT_TYPE.equals(event.eventType()) || !"compliance".equals(event.sourceService()) || event.schemaVersion() != 1
            || payload == null || !payload.path("roleType").isString() || !payload.path("roleId").isString()
            || !payload.path("status").isString()) {
            throw new IllegalArgumentException("Qualification change message is invalid");
        }
        return new ApplyQualificationChangeRequest(payload.path("roleType").asString(), payload.path("roleId").asString(),
            payload.path("status").asString(), ClientReviewResultMessageConverter.optionalId(payload, "decisionId"));
    }
}
