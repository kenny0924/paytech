package com.paytech.client.master.domainservice.service;
import com.paytech.client.master.domain.onboarding.ClientProjectionModels.Source;
/** 在 CCT 本地一致性读取内恢复不可变工作区投影来源。 */
public interface ClientProjectionSourceService {
    Source source(long changeVersionId);
    Source sourceAt(long clientId, int sourceVersion);
}
