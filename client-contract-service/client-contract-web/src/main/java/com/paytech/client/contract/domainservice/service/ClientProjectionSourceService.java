package com.paytech.client.contract.domainservice.service;
import com.paytech.client.contract.domain.onboarding.ClientProjectionModels.Source;
/** 在 CCT 本地一致性读取内恢复不可变工作区投影来源。 */
public interface ClientProjectionSourceService {
    Source source(long changeVersionId);
    Source sourceAt(long clientId, int sourceVersion);
}
