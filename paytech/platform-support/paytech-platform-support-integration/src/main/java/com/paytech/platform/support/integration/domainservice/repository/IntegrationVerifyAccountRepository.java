package com.paytech.platform.support.integration.domainservice.repository;

import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationVerifyAccount;
import java.time.Instant;
import java.util.List;

/** 验证账号的单表仓储；事务由调用方持有，不向其他模块发布 DataObject。 */
public interface IntegrationVerifyAccountRepository {

    /** 按接入方与不可变编码读取账号。 */
    IntegrationVerifyAccount byCode(long integrationId, String code);

    /** 按内部标识读取账号。 */
    IntegrationVerifyAccount byId(long verifyAccountId);

    /** 在调用方事务中锁定账号行，供版本校验及条件更新。 */
    IntegrationVerifyAccount lock(long integrationId, String code);

    /** 读取接入方全部验证账号，按编码排序。 */
    List<IntegrationVerifyAccount> allByIntegration(long integrationId);

    /** 插入新账号，接入方内编码由唯一索引保护。 */
    void insert(IntegrationVerifyAccount row);

    /** 按原版本更新账号内容，并递增版本。 */
    void update(IntegrationVerifyAccount row, String accountName, String channelCode, String channelAccountId, String entityNo, String parameters, String credentials, long updatedBy, Instant updateTime);

    /** 按原版本删除账号。 */
    void delete(IntegrationVerifyAccount row);
}
