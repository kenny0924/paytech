package com.paytech.platform.support.integration.core.dal.mapper;

import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationImplementation;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 渠道标准 API 实现持久化，业务 SQL 位于对应 Mapper XML。 */
@Mapper
public interface IntegrationImplementationMapper {

    /** 按接入方与标准 API 读取当前实现。 */
    IntegrationImplementation byKey(@Param("integrationId") long integrationId, @Param("standardApiId") long standardApiId);

    /** 在调用方事务中锁定实现行，供版本校验及条件更新。 */
    IntegrationImplementation lock(@Param("integrationId") long integrationId, @Param("standardApiId") long standardApiId);

    /** 读取接入方的全部实现。 */
    List<IntegrationImplementation> allByIntegration(@Param("integrationId") long integrationId);

    /** 读取实现某个标准 API 的全部接入方实现，供引用保护与影响校验。 */
    List<IntegrationImplementation> allByStandardApi(@Param("standardApiId") long standardApiId);

    /** 按接入方批量读取实现，供列表展示已实现的标准 API。 */
    List<IntegrationImplementation> allByIntegrations(@Param("integrationIds") Collection<Long> integrationIds);

    /** 按标准 API 批量读取实现，供列表展示实现方。 */
    List<IntegrationImplementation> allByStandardApis(@Param("standardApiIds") Collection<Long> standardApiIds);

    /** 插入新实现，同一接入方与标准 API 由唯一索引保护。 */
    int insert(@Param("row") IntegrationImplementation row);

    /** 按原版本更新流程定义，并递增版本。 */
    int update(@Param("row") IntegrationImplementation row, @Param("flowDefinitionJson") String flowDefinitionJson, @Param("updatedBy") long updatedBy, @Param("updateTime") Instant updateTime);

    /** 按原版本删除实现。 */
    int delete(@Param("row") IntegrationImplementation row);
}
