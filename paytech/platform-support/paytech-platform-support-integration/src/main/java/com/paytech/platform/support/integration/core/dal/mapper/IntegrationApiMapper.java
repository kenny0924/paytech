package com.paytech.platform.support.integration.core.dal.mapper;

import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationApiEndpoint;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationChildCount;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationApi;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 渠道原子 API持久化，业务 SQL 位于对应 Mapper XML。 */
@Mapper
public interface IntegrationApiMapper {

    /** 按接入方与不可变编码读取原子 API。 */
    IntegrationApi byCode(@Param("integrationId") long integrationId, @Param("code") String code);

    /** 在调用方事务中锁定原子 API 行，供版本校验及条件更新。 */
    IntegrationApi lock(@Param("integrationId") long integrationId, @Param("code") String code);

    /** 读取接入方全部原子 API，按编码排序；每个接入方最多 200 个。 */
    List<IntegrationApi> allByIntegration(@Param("integrationId") long integrationId);

    /** 分页查询，按编码与标识稳定排序。 */
    List<IntegrationApi> list(@Param("integrationId") long integrationId, @Param("keyword") String keyword, @Param("offset") long offset, @Param("size") int size);

    /** 使用与分页相同的筛选统计总数。 */
    long count(@Param("integrationId") long integrationId, @Param("keyword") String keyword);

    /** 按接入方统计原子 API 数量，没有原子 API 的接入方不返回。 */
    List<IntegrationChildCount> countByIntegrations(@Param("integrationIds") Collection<Long> integrationIds);

    /** 读取接入方全部原子 API 引用的出站地址代码，按编码排序。 */
    List<IntegrationApiEndpoint> endpointReferences(@Param("integrationId") long integrationId);

    /** 插入新原子 API，接入方内编码由唯一索引保护。 */
    int insert(@Param("row") IntegrationApi row);

    /** 按原版本更新名称、说明与执行定义，并递增版本。 */
    int update(@Param("row") IntegrationApi row, @Param("apiName") String apiName, @Param("description") String description, @Param("definitionJson") String definitionJson, @Param("updatedBy") long updatedBy, @Param("updateTime") Instant updateTime);

    /** 按原版本删除原子 API。 */
    int delete(@Param("row") IntegrationApi row);
}
