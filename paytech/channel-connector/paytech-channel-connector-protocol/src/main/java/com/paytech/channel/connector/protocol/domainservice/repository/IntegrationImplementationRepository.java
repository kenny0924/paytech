package com.paytech.channel.connector.protocol.domainservice.repository;

import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationImplementation;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** 渠道标准 API 实现的单表仓储；事务由调用方持有，不向其他模块发布 DataObject。 */
public interface IntegrationImplementationRepository {

    /** 按接入方与标准 API 读取当前实现。 */
    IntegrationImplementation byKey(long integrationId, long standardApiId);

    /** 在调用方事务中锁定实现行，供版本校验及条件更新。 */
    IntegrationImplementation lock(long integrationId, long standardApiId);

    /** 读取接入方的全部实现。 */
    List<IntegrationImplementation> allByIntegration(long integrationId);

    /** 读取实现某个标准 API 的全部接入方实现，供引用保护与影响校验。 */
    List<IntegrationImplementation> allByStandardApi(long standardApiId);

    /** 按接入方批量读取实现，供列表展示已实现的标准 API。 */
    List<IntegrationImplementation> allByIntegrations(Collection<Long> integrationIds);

    /** 按标准 API 批量读取实现；空集合返回空列表。 */
    List<IntegrationImplementation> allByStandardApis(Collection<Long> standardApiIds);

    /** 插入新实现，同一接入方与标准 API 由唯一索引保护。 */
    void insert(IntegrationImplementation row);

    /** 按原版本更新流程定义，并递增版本。 */
    void update(IntegrationImplementation row, String flowDefinitionJson, long updatedBy, Instant updateTime);

    /** 按原版本删除实现。 */
    void delete(IntegrationImplementation row);
}
