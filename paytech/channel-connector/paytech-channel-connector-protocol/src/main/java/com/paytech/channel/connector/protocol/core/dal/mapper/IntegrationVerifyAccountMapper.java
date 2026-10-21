package com.paytech.channel.connector.protocol.core.dal.mapper;

import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyAccount;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 验证账号持久化，业务 SQL 位于对应 Mapper XML。 */
@Mapper
public interface IntegrationVerifyAccountMapper {

    /** 按接入方与不可变编码读取账号。 */
    IntegrationVerifyAccount byCode(@Param("integrationId") long integrationId, @Param("code") String code);

    /** 按内部标识读取账号。 */
    IntegrationVerifyAccount byId(@Param("verifyAccountId") long verifyAccountId);

    /** 在调用方事务中锁定账号行，供版本校验及条件更新。 */
    IntegrationVerifyAccount lock(@Param("integrationId") long integrationId, @Param("code") String code);

    /** 读取接入方全部验证账号，按编码排序。 */
    List<IntegrationVerifyAccount> allByIntegration(@Param("integrationId") long integrationId);

    /** 插入新账号，接入方内编码由唯一索引保护。 */
    int insert(@Param("row") IntegrationVerifyAccount row);

    /** 按原版本更新账号内容，并递增版本。 */
    int update(@Param("row") IntegrationVerifyAccount row, @Param("accountName") String accountName, @Param("channelCode") String channelCode, @Param("channelAccountId") String channelAccountId, @Param("entityNo") String entityNo, @Param("parameters") String parameters, @Param("credentials") String credentials, @Param("updatedBy") long updatedBy, @Param("updateTime") Instant updateTime);

    /** 按原版本删除账号。 */
    int delete(@Param("row") IntegrationVerifyAccount row);
}
