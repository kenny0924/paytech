package com.paytech.client.contract.domainservice.repository;

import com.paytech.client.contract.core.dal.dataobject.ClientProjectionHistory;

/** 原始生命周期来源读取，不将日常表单当作历史事实。 */
public interface ClientProjectionRepository {
    ClientProjectionHistory source(long changeId);
    ClientProjectionHistory sourceAt(long clientId, int version);
}
