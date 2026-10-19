package com.paytech.commercial.domainservice.repository.impl;

import java.util.List;

import com.paytech.commercial.core.dal.dataobject.BusinessChangeVersion;
import com.paytech.commercial.core.dal.mapper.BusinessChangeVersionMapper;
import com.paytech.commercial.domainservice.repository.BusinessChangeVersionRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 BusinessChangeVersionMapper 读写 t_business_change_version。 */
@AllArgsConstructor
public class BusinessChangeVersionRepositoryImpl implements BusinessChangeVersionRepository {
    private final BusinessChangeVersionMapper mapper;

    /** 插入一条不可变快照，创建时间截断到微秒后写回对象。 */
    @Override
    public void insert(BusinessChangeVersion version) {
        version.setCreateTime(PersistenceTimes.micros(version.getCreateTime()));
        if (mapper.insert(version) != 1) {
            throw new ErrorException("业务变更快照插入失败: history " + version.getHistoryId());
        }
    }

    /** 读取一条历史身份的全部快照。 */
    @Override
    public List<BusinessChangeVersion> findByHistoryId(long historyId) {
        return List.copyOf(mapper.findByHistoryId(historyId));
    }
}
