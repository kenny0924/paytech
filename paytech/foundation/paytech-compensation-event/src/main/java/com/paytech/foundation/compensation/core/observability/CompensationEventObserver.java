package com.paytech.foundation.compensation.core.observability;

import java.time.Duration;

/**
 * 低基数执行观测接口。实现类不得使用 Payload 或来源标识作为标签。
 *
 * @author Codex
 */
public interface CompensationEventObserver {

    default void registrationFinished(String eventType, boolean duplicate) {}

    default void claimFinished(String eventType, boolean leaseTakeover) {}

    default void executorRejected() {}

    default void handlerCapacityRejected() {}

    default void attemptFinished(String eventType, String result, Duration duration) {}

    default void staleTransition(String eventType, String result) {}

    default void eventFailed(String eventType, String reason) {}

    default void manualReopened(String eventType) {}

    default void backlogObserved(CompensationBacklogSnapshot snapshot) {}
}
