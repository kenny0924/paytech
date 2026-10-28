package com.paytech.platform.support.referencedata.client;

import java.util.List;

/**
 * 立即从 Platform 全量刷新已开启的种类（生产入口，客户端的调度线程也经它刷新）。
 * <li>会发起 HTTP，不得在数据库事务内调用；读方法在 {@link ReferenceData}。</li>
 * <li>{@code scheduling-enabled=false} 时这是唯一的刷新途径（自动测试在配置好测试端点后调用）。</li>
 */
public interface ReferenceDataRefresher {

    /**
     * 同步刷新全部已开启的种类；每类独立成功或失败，失败的种类保留旧快照
     *
     * @return 每个已开启种类的结果，按种类声明顺序
     */
    List<Result> refreshNow();

    /**
     * 一个种类的刷新结果。
     *
     * @param kind 种类
     * @param succeeded 是否已整体替换为新快照
     * @param count 成功时为新快照条数，失败时为 0
     * @param failure 失败时的异常类名（不含响应内容），成功时为 null
     */
    record Result(ReferenceDataKind kind, boolean succeeded, int count, String failure) {
    }
}
