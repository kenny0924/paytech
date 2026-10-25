package com.paytech.client.master.domainservice.repository.impl;

import java.time.Instant;

import com.paytech.client.master.core.dal.mapper.BusinessChangeVersionMapper;
import com.paytech.client.master.domainservice.repository.BusinessChangeVersionRepository;

import lombok.AllArgsConstructor;

/** 只通过 BusinessChangeVersionMapper 追加 t_business_change_version。 */
@AllArgsConstructor
public class BusinessChangeVersionRepositoryImpl implements BusinessChangeVersionRepository {
    private final BusinessChangeVersionMapper mapper;

    /** 追加一条 UPDATE 快照。 */
    @Override
    public void insertUpdate(long changeVersionId, long historyId, long versionNo, String snapshotJson, long createBy, Instant now) {
        mapper.insertUpdate(changeVersionId, historyId, versionNo, snapshotJson, createBy, now);
    }
}
