package com.paytech.platform.support.integration.domainservice.service;

import java.time.Instant;

import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationVerifyAccount;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationVerifyCase;

/** 验证账号与验证用例的本地保存；事务由调用方持有，版本保护由 Repository 条件更新完成。 */
public interface IntegrationVerifyConfigService {

    /** 插入新账号。 */
    void createAccount(IntegrationVerifyAccount account);

    /** 按原版本整体更新账号可编辑字段，成功后同步原对象。 */
    void updateAccount(IntegrationVerifyAccount account, String name, String channelCode, String channelAccountId,
        String entityNo, String parametersJson, String credentialsJson, long updatedBy, Instant updateTime);

    /** 按原版本删除账号。 */
    void deleteAccount(IntegrationVerifyAccount account);

    /** 插入新用例。 */
    void createCase(IntegrationVerifyCase verifyCase);

    /** 按原版本整体更新用例可编辑字段，成功后同步原对象。 */
    void updateCase(IntegrationVerifyCase verifyCase, String name, String description, long verifyAccountId,
        String stepsJson, long updatedBy, Instant updateTime);

    /** 按原版本删除用例；运行记录保留。 */
    void deleteCase(IntegrationVerifyCase verifyCase);
}
