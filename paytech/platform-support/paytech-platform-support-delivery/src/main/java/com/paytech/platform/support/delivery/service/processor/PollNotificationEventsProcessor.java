package com.paytech.platform.support.delivery.service.processor;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.delivery.core.constant.NotificationProviderTypes;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationProvider;
import com.paytech.platform.support.delivery.core.enums.NotificationEventResult;
import com.paytech.platform.support.delivery.domain.NotificationProviderEvent;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationProviderRepository;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEventBatch;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEventConfiguration;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEventMessage;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEventParser;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEventProperties;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEventQueue;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEventReceiver;
import com.paytech.platform.support.delivery.service.NotificationEventService;
import com.paytech.platform.support.delivery.service.processor.request.PollNotificationEventsRequest;
import com.paytech.platform.support.delivery.service.processor.response.PollNotificationEventsResponse;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;

/**
 * 拉取一个发送账号事件队列中的 SES 投递事件并回写发送记录
 * <li>Vault 读取、接收与删除消息都在事务外；每条事件由 NotificationEventService 在独立短事务内回写，提交后才删除</li>
 * <li>无法解析、尝试仍在发送中或本地冲突的消息不删除，到可见性超时后再次处理；无法解析的最终进入死信队列</li>
 * <li>拉取失败只记账号运行状态与指标，不影响发信；消息体可能含收件地址，不进入日志、异常或指标</li>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression(SesEventConfiguration.ENABLED)
public class PollNotificationEventsProcessor
    extends AbstractProcessor<PollNotificationEventsRequest, PollNotificationEventsResponse> {

    private final Clock clock;
    private final SesEventQueue queue;
    private final ObjectProvider<MeterRegistry> meters;
    private final SesEventProperties properties;
    private final NotificationEventService events;
    private final NotificationProviderRepository providers;

    @Override
    protected PollNotificationEventsResponse processRequest(PollNotificationEventsRequest request) {
        NotificationProvider account = providers.find(request.getProviderId());
        if (!isEventSource(account)) {
            return new PollNotificationEventsResponse(0, null);
        }
        SesEventReceiver receiver;
        try {
            receiver = queue.prepare(account);
        } catch (FailException | ErrorException unavailable) {
            // Vault 不可用或值不合法：本轮跳过，原因只计错误码，不含 Token 或密钥。
            return finish(account, "CREDENTIAL_UNAVAILABLE", 0);
        }
        int received = 0;
        for (int batch = 0; batch < properties.getMaxBatches(); batch++) {
            SesEventBatch result = receiver.receive();
            if (result.getError() != null) {
                return finish(account, result.getError(), received);
            }
            received += result.getMessages().size();
            String deleteError = consume(account, receiver, result);
            if (deleteError != null) {
                return finish(account, deleteError, received);
            }
            if (result.getMessages().size() < SesEventQueue.BATCH_SIZE) {
                break;
            }
        }
        return finish(account, null, received);
    }

    /** 仍为 SES 类型且已配置事件队列与密钥；账号在两轮之间被修改时跳过。 */
    boolean isEventSource(NotificationProvider account) {
        return account != null && NotificationProviderTypes.AMAZON_SES.equals(account.getProviderType())
            && account.getEventQueueUrl() != null && account.getCredentialVaultToken() != null;
    }

    /**
     * 逐条处理一批消息，已消费的消息随即删除
     *
     * @return 删除失败时的错误码；全部成功为空
     */
    String consume(NotificationProvider account, SesEventReceiver receiver, SesEventBatch batch) {
        String deleteError = null;
        for (SesEventMessage message : batch.getMessages()) {
            NotificationEventResult result = handle(account, message);
            if (!result.isConsumed()) {
                continue;
            }
            String error = receiver.delete(message);
            if (error != null) {
                deleteError = error;
            }
        }
        return deleteError;
    }

    /** 解析并回写一条消息；本地并发冲突按延后处理。 */
    NotificationEventResult handle(NotificationProvider account, SesEventMessage message) {
        NotificationProviderEvent event = SesEventParser.parse(message.getBody());
        NotificationEventResult result;
        if (event == null) {
            result = NotificationEventResult.INVALID;
        } else if (event.isIgnored()) {
            result = NotificationEventResult.IGNORED;
        } else {
            result = apply(event);
        }
        count(account, event == null ? "OTHER" : event.getEventType(), result);
        return result;
    }

    /** 冲突说明其他执行者刚推进了同一投递，保留消息下次再读新事实。 */
    NotificationEventResult apply(NotificationProviderEvent event) {
        try {
            return events.apply(event, Instant.now(clock));
        } catch (ConcurrentRequestException changed) {
            return NotificationEventResult.DEFERRED;
        }
    }

    /** 记录本轮运行状态与拉取指标，返回结果摘要。 */
    PollNotificationEventsResponse finish(NotificationProvider account, String error, int received) {
        events.recordPoll(account.getProviderId(), error, received > 0, Instant.now(clock));
        MeterRegistry registry = meters.getIfAvailable();
        if (registry != null) {
            registry.counter("notification.provider.event.polls", "provider", account.getProviderCode(), "result",
                error == null ? "SUCCESS" : error).increment();
        }
        return new PollNotificationEventsResponse(received, error);
    }

    /** 事件指标；标签不含收件地址、消息标识或尝试身份。 */
    void count(NotificationProvider account, String eventType, NotificationEventResult result) {
        MeterRegistry registry = meters.getIfAvailable();
        if (registry != null) {
            registry.counter("notification.provider.events", "provider", account.getProviderCode(), "event", eventType,
                "result", result.getCode()).increment();
        }
    }
}
