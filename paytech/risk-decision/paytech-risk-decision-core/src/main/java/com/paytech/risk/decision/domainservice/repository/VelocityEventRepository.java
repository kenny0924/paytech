package com.paytech.risk.decision.domainservice.repository;

import com.paytech.risk.decision.core.dal.dataobject.VelocityEvent;
import java.util.List;

/** 风险频次事件的单表持久化边界。 */
public interface VelocityEventRepository {
    /** 批量插入；空集合不访问数据库。 */
    void insertAll(List<VelocityEvent> rows);
}
