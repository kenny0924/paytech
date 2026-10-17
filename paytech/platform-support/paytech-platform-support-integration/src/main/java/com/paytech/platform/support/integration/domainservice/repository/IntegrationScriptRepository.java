package com.paytech.platform.support.integration.domainservice.repository;

import java.util.Collection;
import java.util.Map;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationScript;
import com.paytech.platform.support.api.enums.ScriptExecutionStage;
import java.time.Instant;
import java.util.List;

/** 公共脚本的单表仓储；事务由调用方持有，不向其他模块发布 DataObject。 */
public interface IntegrationScriptRepository {

    /** 按接入方与不可变编码读取脚本。 */
    IntegrationScript byCode(long integrationId, String code);

    /** 在调用方事务中锁定脚本行，供版本校验及条件更新。 */
    IntegrationScript lock(long integrationId, String code);

    /** 读取接入方全部脚本，按编码排序；每个接入方最多 100 个。 */
    List<IntegrationScript> allByIntegration(long integrationId);

    /** 分页查询，可按阶段筛选，按编码与标识稳定排序。 */
    List<IntegrationScript> list(long integrationId, String keyword, ScriptExecutionStage executionStage, long offset, int size);

    /** 使用与分页相同的筛选统计总数。 */
    long count(long integrationId, String keyword, ScriptExecutionStage executionStage);

    /** 按接入方统计公共脚本数量；空集合返回空映射。 */
    Map<Long, Long> countByIntegrations(Collection<Long> integrationIds);

    /** 插入新脚本，接入方内编码由唯一索引保护。 */
    void insert(IntegrationScript row);

    /** 按原版本更新脚本内容，并递增版本。 */
    void update(IntegrationScript row, String scriptName, ScriptExecutionStage executionStage, String parameterDefinition, String sourceCode, String description, long updatedBy, Instant updateTime);

    /** 按原版本删除脚本。 */
    void delete(IntegrationScript row);
}
