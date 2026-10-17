package com.paytech.platform.support.delivery.service.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationAttempt;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationDelivery;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationProvider;
import com.paytech.platform.support.delivery.core.enums.NotificationAttemptStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationDeliveryStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationEventOutcome;
import com.paytech.platform.support.delivery.core.enums.NotificationEventResult;
import com.paytech.platform.support.delivery.core.enums.NotificationResolutionSource;
import com.paytech.platform.support.delivery.domain.NotificationProviderEvent;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationAttemptRepository;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationDeliveryRepository;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationProviderRepository;
import com.paytech.platform.support.delivery.service.NotificationEventService;

import lombok.RequiredArgsConstructor;

/**
 * 供应商投递事件回写
 * <li>事件记在它对应的尝试上；投递状态按"当前尝试"规则推进，旧尝试送达也视为已送达</li>
 * <li>结果未知的尝试收到任何事件即证明已受理，改为 ACCEPTED，来源 PROVIDER_EVENT</li>
 */
@Service
@RequiredArgsConstructor
public class NotificationEventServiceImpl implements NotificationEventService {

    private final PlatformTransactionManager transactionManager;
    private final NotificationAttemptRepository attempts;
    private final NotificationDeliveryRepository deliveries;
    private final NotificationProviderRepository providers;

    /** 已配置事件队列且已录入密钥的账号，按编码升序。 */
    @Override
    public List<NotificationProvider> eventSources(String providerType) {
        return providers.findEventSources(providerType);
    }

    /** 在一个短事务内回写；冲突回滚后由调用方保留消息。 */
    @Override
    public NotificationEventResult apply(NotificationProviderEvent event, Instant now) {
        return transaction().execute(tx -> applyWithinTransaction(event, now));
    }

    /** 运行状态覆盖写，不需要事务。 */
    @Override
    public void recordPoll(long providerId, String error, boolean received, Instant now) {
        providers.recordEventPoll(providerId, error, received, now);
    }

    /**
     * 匹配尝试 → 确认受理 → 记录结果或投诉 → 推进投递
     * <li>标签缺失、尝试不存在或消息标识不一致视为匹配不到；尝试仍在发送中延后处理</li>
     */
    NotificationEventResult applyWithinTransaction(NotificationProviderEvent event, Instant now) {
        NotificationAttempt attempt = event.getAttemptId() == null ? null : attempts.find(event.getAttemptId());
        if (!matches(attempt, event)) {
            return NotificationEventResult.UNMATCHED;
        }
        if (attempt.getStatus() == NotificationAttemptStatus.PROCESSING) {
            return NotificationEventResult.DEFERRED;
        }
        NotificationDelivery delivery = deliveries.find(attempt.getDeliveryId());
        NotificationAttempt current = attempts.findCurrent(attempt.getDeliveryId());
        if (delivery == null || current == null) {
            throw new ErrorException("通知尝试缺少所属投递: " + attempt.getAttemptId());
        }
        boolean confirmed = confirmAccepted(attempt, event, now);
        boolean recorded = event.isComplaint() ? recordComplaint(attempt, event, now) : recordOutcome(attempt, event, now);
        var target = targetStatus(delivery.getStatus(), attempt.getEventOutcome(),
            current.getAttemptId().equals(attempt.getAttemptId()), confirmed);
        boolean advanced = target != delivery.getStatus();
        if (advanced) {
            deliveries.transition(delivery, target, delivery.getAttemptCount(), null, now);
        }
        return confirmed || recorded || advanced ? NotificationEventResult.APPLIED : NotificationEventResult.DUPLICATE;
    }

    /** 事件属于该尝试：尝试存在、未在提交时被拒，且已知消息标识与事件一致。 */
    boolean matches(NotificationAttempt attempt, NotificationProviderEvent event) {
        return attempt != null && attempt.getStatus() != NotificationAttemptStatus.FAILED
            && (attempt.getProviderMessageId() == null || attempt.getProviderMessageId().equals(event.getMessageId()));
    }

    /** 结果未知的尝试由事件证明已受理；返回是否发生确认。 */
    boolean confirmAccepted(NotificationAttempt attempt, NotificationProviderEvent event, Instant now) {
        if (attempt.getStatus() != NotificationAttemptStatus.UNKNOWN) {
            return false;
        }
        attempts.resolve(attempt, NotificationAttemptStatus.ACCEPTED, event.getMessageId(),
            "EVENT_" + event.getEventType(), NotificationResolutionSource.PROVIDER_EVENT, now);
        return true;
    }

    /** 只在新结果优先级更高时覆盖；返回是否写入。 */
    boolean recordOutcome(NotificationAttempt attempt, NotificationProviderEvent event, Instant now) {
        if (!event.getOutcome().overrides(attempt.getEventOutcome())) {
            return false;
        }
        attempts.recordEventOutcome(attempt, event.getOutcome(), event.getOutcomeCode(), event.getOccurredTime(), now);
        return true;
    }

    /** 只记录首个投诉；返回是否写入。 */
    boolean recordComplaint(NotificationAttempt attempt, NotificationProviderEvent event, Instant now) {
        if (attempt.getComplaintTime() != null) {
            return false;
        }
        attempts.recordComplaint(attempt, event.getComplaintCode(), event.getOccurredTime(), now);
        return true;
    }

    /**
     * 投递应处的状态
     * <li>送达：已发送、结果未知、等待重发 → 已送达（旧尝试送达同样适用，收件人已收到）</li>
     * <li>硬退信、拒发：仅当前尝试，已发送、结果未知、等待重发、已送达 → 失败</li>
     * <li>仅确认受理：当前尝试的结果未知、等待重发 → 已发送，避免再次重发</li>
     * <li>发送中、待发送、已失败不改变</li>
     */
    static NotificationDeliveryStatus targetStatus(NotificationDeliveryStatus status, NotificationEventOutcome outcome,
        boolean current, boolean confirmed) {
        boolean open = status == NotificationDeliveryStatus.SENT || status == NotificationDeliveryStatus.UNKNOWN
            || status == NotificationDeliveryStatus.RETRY_WAIT;
        if (outcome == NotificationEventOutcome.DELIVERED && open) {
            return NotificationDeliveryStatus.DELIVERED;
        }
        if (outcome != null && outcome.undeliverable() && current
            && (open || status == NotificationDeliveryStatus.DELIVERED)) {
            return NotificationDeliveryStatus.FAILED;
        }
        if (confirmed && current && status != NotificationDeliveryStatus.SENT && open) {
            return NotificationDeliveryStatus.SENT;
        }
        return status;
    }

    /** 读写事务，可重复读，与通知状态机其他写入一致。 */
    TransactionTemplate transaction() {
        var tx = new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.setTimeout(15);
        return tx;
    }
}
