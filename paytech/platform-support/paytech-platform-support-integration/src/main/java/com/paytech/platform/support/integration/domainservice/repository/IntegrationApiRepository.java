package com.paytech.platform.support.integration.domainservice.repository;

import java.util.Collection;
import java.util.Map;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationApiEndpoint;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationApi;
import java.time.Instant;
import java.util.List;

/** 渠道原子 API的单表仓储；事务由调用方持有，不向其他模块发布 DataObject。 */
public interface IntegrationApiRepository {

    /** 按接入方与不可变编码读取原子 API。 */
    IntegrationApi byCode(long integrationId, String code);

    /** 在调用方事务中锁定原子 API 行，供版本校验及条件更新。 */
    IntegrationApi lock(long integrationId, String code);

    /** 读取接入方全部原子 API，按编码排序；每个接入方最多 200 个。 */
    List<IntegrationApi> allByIntegration(long integrationId);

    /** 分页查询，按编码与标识稳定排序。 */
    List<IntegrationApi> list(long integrationId, String keyword, long offset, int size);

    /** 使用与分页相同的筛选统计总数。 */
    long count(long integrationId, String keyword);

    /** 按接入方统计原子 API 数量；空集合返回空映射。 */
    Map<Long, Long> countByIntegrations(Collection<Long> integrationIds);

    /** 读取接入方全部原子 API 引用的出站地址代码，按编码排序。 */
    List<IntegrationApiEndpoint> endpointReferences(long integrationId);

    /** 插入新原子 API，接入方内编码由唯一索引保护。 */
    void insert(IntegrationApi row);

    /** 按原版本更新名称、说明与执行定义，并递增版本。 */
    void update(IntegrationApi row, String apiName, String description, String definitionJson, long updatedBy, Instant updateTime);

    /** 按原版本删除原子 API。 */
    void delete(IntegrationApi row);
}
