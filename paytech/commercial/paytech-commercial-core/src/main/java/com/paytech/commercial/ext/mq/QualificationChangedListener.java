package com.paytech.commercial.ext.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paytech.commercial.service.processor.RecheckForQualificationChangeProcessor;
import com.paytech.commercial.service.processor.request.RecheckForQualificationChangeRequest;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.messaging.api.ConsumeTransaction;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventConsumer;
import com.paytech.foundation.messaging.api.DomainEventHandler;
import com.paytech.foundation.messaging.api.DomainEventRejectedException;

import tools.jackson.databind.JsonNode;

/**
 * 消费 compliance QualificationChanged v1（commercial.compliance 分区队列；开通项前置门禁 technique.md §11.2）
 * <li>事件只触发受影响 CLIENT 合同的开通项前置重查，结论以同步资格查询为准；重复投递再查一次即可，不另做业务幂等。</li>
 * <li>HANDLER_MANAGED：门禁 Processor 拒绝调用方事务并自行开短事务，所以 Handler 在消费登记事务之外执行。</li>
 * <li>信封不合法或业务拒绝进入死信；合同锁冲突、资格服务不可用与系统错误原样抛出重投。</li>
 */
@DomainEventHandler(eventType = QualificationChangedListener.EVENT_TYPE, strictOrder = false,
    transaction = ConsumeTransaction.HANDLER_MANAGED)
public class QualificationChangedListener implements DomainEventConsumer {
    /** 事件类型。 */
    static final String EVENT_TYPE = "QualificationChanged";

    /** 发布方服务名。 */
    static final String SOURCE_SERVICE = "compliance";

    private static final Logger LOG = LoggerFactory.getLogger(QualificationChangedListener.class);

    private final RecheckForQualificationChangeProcessor processor;

    /**
     * 只依赖资格变化重查用例
     *
     * @param processor 重查用例
     */
    public QualificationChangedListener(RecheckForQualificationChangeProcessor processor) {
        this.processor = processor;
    }

    /** 日志只记录事件 ID 与结果计数，不记录正文。 */
    @Override
    public void consume(DomainEvent event) {
        try {
            var result = processor.process(convert(event));
            LOG.info("Qualification change {} rechecked {} agreements, {} changed", event.eventId(),
                result.getCheckedCount(), result.getChangedCount());
        } catch (ConcurrentRequestException | ErrorException retryable) {
            LOG.warn("Qualification change {} recheck will be redelivered", event.eventId());
            throw retryable;
        } catch (IllegalArgumentException | FailException rejected) {
            LOG.warn("Qualification change {} rejected to dead letter", event.eventId());
            throw new DomainEventRejectedException("资格变化事件被拒绝: " + event.eventId(), rejected);
        }
    }

    /** 校验信封身份并转换；Payload 字段见 compliance QualificationChangedPayload，只取 roleType 与 roleId。 */
    static RecheckForQualificationChangeRequest convert(DomainEvent event) {
        JsonNode payload = event.payload();
        if (!EVENT_TYPE.equals(event.eventType()) || !SOURCE_SERVICE.equals(event.sourceService())
            || event.schemaVersion() != 1 || payload == null || !payload.path("roleType").isString()
            || !payload.path("roleId").isString()) {
            throw new IllegalArgumentException("Qualification change message is invalid");
        }
        return new RecheckForQualificationChangeRequest(payload.path("roleType").asString(),
            payload.path("roleId").asString());
    }
}
