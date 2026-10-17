package com.paytech.client.master.domainservice.repository;

import com.paytech.client.master.core.dal.dataobject.ClientProjectionHistory;

/** 原始生命周期来源读取，不将日常表单当作历史事实。 */
public interface ClientProjectionRepository {
    ClientProjectionHistory source(long changeId);
    ClientProjectionHistory sourceAt(long clientId, int version);
    /** 某 Client 当前最新的生命周期历史；不存在返回 null。 */
    ClientProjectionHistory latest(long clientId);
    /**
     * 追加下一版生命周期历史并推进历史根
     * <li>根已不是前一版本时抛出并发冲突，由外层事务回滚。</li>
     */
    void append(ClientProjectionHistory next, String snapshot);
}
