package com.paytech.foundation.compensation.api;

/**
 * 在调用方当前事务中登记可靠异步任务。
 *
 * @author Codex
 */
@FunctionalInterface
public interface CompensationEventRegistrar {

    /**
     * 登记不可变补偿事件。
     *
     * @param request
     *            事件登记请求
     * @return 登记结果
     */
    RegisteredCompensationEvent register(CompensationEventRequest request);
}
