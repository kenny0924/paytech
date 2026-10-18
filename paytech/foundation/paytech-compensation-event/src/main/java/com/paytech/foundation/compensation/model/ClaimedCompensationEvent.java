package com.paytech.foundation.compensation.model;

import java.time.LocalDateTime;

import com.paytech.foundation.compensation.api.CompensationEvent;

/**
 * 单次已认领执行尝试的不可变围栏令牌。
 *
 * @author Codex
 */
public record ClaimedCompensationEvent(long id, String eventKey, String eventType, String subEventType,
    String payloadJson, int executeCount, String lockOwner, LocalDateTime lockUntil, long lockVersion,
    boolean leaseTakeover) {

    public static ClaimedCompensationEvent from(CompensationEventRecord record, boolean leaseTakeover) {
        return new ClaimedCompensationEvent(record.id(), record.eventKey(), record.eventType(), record.subEventType(),
            record.payloadJson(), record.executeCount(), record.lockOwner(), record.lockUntil(), record.lockVersion(),
            leaseTakeover);
    }

    public CompensationEvent toEvent() {
        return new CompensationEvent(id, eventKey, eventType, subEventType, payloadJson, executeCount);
    }
}
