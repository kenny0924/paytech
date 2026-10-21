package com.paytech.channel.connector.protocol.core.dal.mapper;

import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyCase;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 验证用例持久化，业务 SQL 位于对应 Mapper XML。 */
@Mapper
public interface IntegrationVerifyCaseMapper {

    /** 按接入方与不可变编码读取用例。 */
    IntegrationVerifyCase byCode(@Param("integrationId") long integrationId, @Param("code") String code);

    /** 在调用方事务中锁定用例行，供版本校验及条件更新。 */
    IntegrationVerifyCase lock(@Param("integrationId") long integrationId, @Param("code") String code);

    /** 分页查询，按编码与标识稳定排序。 */
    List<IntegrationVerifyCase> list(@Param("integrationId") long integrationId, @Param("keyword") String keyword, @Param("offset") long offset, @Param("size") int size);

    /** 使用与分页相同的筛选统计总数。 */
    long count(@Param("integrationId") long integrationId, @Param("keyword") String keyword);

    /** 统计引用某验证账号的用例数量。 */
    long countByAccount(@Param("verifyAccountId") long verifyAccountId);

    /** 插入新用例，接入方内编码由唯一索引保护。 */
    int insert(@Param("row") IntegrationVerifyCase row);

    /** 按原版本更新用例内容，并递增版本。 */
    int update(@Param("row") IntegrationVerifyCase row, @Param("caseName") String caseName, @Param("description") String description, @Param("verifyAccountId") long verifyAccountId, @Param("stepsJson") String stepsJson, @Param("updatedBy") long updatedBy, @Param("updateTime") Instant updateTime);

    /** 按原版本删除用例；运行记录保留。 */
    int delete(@Param("row") IntegrationVerifyCase row);
}
