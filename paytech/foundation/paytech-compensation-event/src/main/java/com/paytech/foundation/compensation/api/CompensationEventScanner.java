package com.paytech.foundation.compensation.api;

/**
 * 查找已到执行时间或租约已过期的事件，并提交有界执行唤醒任务。
 *
 * @author Codex
 */
@FunctionalInterface
public interface CompensationEventScanner {

    /**
     * 在调用线程不认领记录的情况下扫描一个稳定分片。
     *
     * @param request
     *            有界扫描请求
     * @return 提交结果摘要
     */
    CompensationScanResult scan(CompensationScanRequest request);
}
