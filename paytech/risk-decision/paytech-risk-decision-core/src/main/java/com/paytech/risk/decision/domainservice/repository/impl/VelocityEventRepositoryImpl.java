package com.paytech.risk.decision.domainservice.repository.impl;

import com.paytech.risk.decision.core.dal.dataobject.VelocityEvent;
import com.paytech.risk.decision.core.dal.mapper.VelocityEventMapper;
import com.paytech.risk.decision.domainservice.repository.VelocityEventRepository;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

/** 风险频次事件持久化。 */
@Repository
@AllArgsConstructor
public class VelocityEventRepositoryImpl implements VelocityEventRepository {
    private final VelocityEventMapper mapper;

    /** 批量插入；空集合不访问数据库。 */
    @Override
    public void insertAll(List<VelocityEvent> rows) {
        if (!rows.isEmpty()) {
            mapper.insertBatch(rows);
        }
    }
}
