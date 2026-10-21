package com.paytech.channel.connector.protocol.core.dal.mapper;

import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationChildCount;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationScript;
import com.paytech.channel.connector.api.enums.ScriptExecutionStage;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 公共脚本持久化，业务 SQL 位于对应 Mapper XML。 */
@Mapper
public interface IntegrationScriptMapper {

    /** 按接入方与不可变编码读取脚本。 */
    IntegrationScript byCode(@Param("integrationId") long integrationId, @Param("code") String code);

    /** 在调用方事务中锁定脚本行，供版本校验及条件更新。 */
    IntegrationScript lock(@Param("integrationId") long integrationId, @Param("code") String code);

    /** 读取接入方全部脚本，按编码排序；每个接入方最多 100 个。 */
    List<IntegrationScript> allByIntegration(@Param("integrationId") long integrationId);

    /** 分页查询，可按阶段筛选，按编码与标识稳定排序。 */
    List<IntegrationScript> list(@Param("integrationId") long integrationId, @Param("keyword") String keyword, @Param("executionStage") ScriptExecutionStage executionStage, @Param("offset") long offset, @Param("size") int size);

    /** 使用与分页相同的筛选统计总数。 */
    long count(@Param("integrationId") long integrationId, @Param("keyword") String keyword, @Param("executionStage") ScriptExecutionStage executionStage);

    /** 按接入方统计公共脚本数量，没有脚本的接入方不返回。 */
    List<IntegrationChildCount> countByIntegrations(@Param("integrationIds") Collection<Long> integrationIds);

    /** 插入新脚本，接入方内编码由唯一索引保护。 */
    int insert(@Param("row") IntegrationScript row);

    /** 按原版本更新脚本内容，并递增版本。 */
    int update(@Param("row") IntegrationScript row, @Param("scriptName") String scriptName, @Param("executionStage") ScriptExecutionStage executionStage, @Param("parameterDefinition") String parameterDefinition, @Param("sourceCode") String sourceCode, @Param("description") String description, @Param("updatedBy") long updatedBy, @Param("updateTime") Instant updateTime);

    /** 按原版本删除脚本。 */
    int delete(@Param("row") IntegrationScript row);
}
