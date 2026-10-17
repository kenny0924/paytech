package com.paytech.platform.support.integration.domainservice.repository.impl;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationVerifyAccount;
import com.paytech.platform.support.integration.core.dal.mapper.IntegrationVerifyAccountMapper;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationVerifyAccountRepository;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** 校验验证账号写入行数，成功后同步原对象；事务由调用方持有。 */
@Repository
@AllArgsConstructor
public class IntegrationVerifyAccountRepositoryImpl implements IntegrationVerifyAccountRepository {

    private final IntegrationVerifyAccountMapper mapper;

    /** 按接入方与不可变编码读取账号。 */
    @Override
    public IntegrationVerifyAccount byCode(long integrationId, String code) {
        return mapper.byCode(integrationId, code);
    }

    /** 按内部标识读取账号。 */
    @Override
    public IntegrationVerifyAccount byId(long verifyAccountId) {
        return mapper.byId(verifyAccountId);
    }

    /** 在调用方事务中锁定账号行，供版本校验及条件更新。 */
    @Override
    public IntegrationVerifyAccount lock(long integrationId, String code) {
        return mapper.lock(integrationId, code);
    }

    /** 读取接入方全部验证账号，按编码排序。 */
    @Override
    public List<IntegrationVerifyAccount> allByIntegration(long integrationId) {
        return mapper.allByIntegration(integrationId);
    }

    /** 插入新账号，接入方内编码由唯一索引保护。 */
    @Override
    public void insert(IntegrationVerifyAccount row) {
        try {
            if (mapper.insert(row) != 1) {
                throw new ConcurrentRequestException("验证账号创建未成功，请刷新后重试");
            }
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_verify_account_code")) {
                throw failure;
            }
            throw new BizCheckFailException("验证账号编码已经存在", failure);
        }
    }

    /** 按原版本更新账号内容，并递增版本。 */
    @Override
    public void update(IntegrationVerifyAccount row, String accountName, String channelCode, String channelAccountId, String entityNo, String parameters, String credentials, long updatedBy, Instant updateTime) {
        int previousVersion = row.getVersion();
        if (previousVersion == Integer.MAX_VALUE || mapper.update(row, accountName, channelCode, channelAccountId, entityNo, parameters, credentials, updatedBy, updateTime) != 1) {
            throw new ConcurrentRequestException("验证账号已被其他操作修改，请刷新后重试");
        }
        row.setAccountName(accountName);
        row.setChannelCode(channelCode);
        row.setChannelAccountId(channelAccountId);
        row.setEntityNo(entityNo);
        row.setParameters(parameters);
        row.setCredentials(credentials);
        row.setVersion(previousVersion + 1);
        row.setUpdatedBy(updatedBy);
        row.setUpdateTime(updateTime);
    }

    /** 按原版本删除账号。 */
    @Override
    public void delete(IntegrationVerifyAccount row) {
        if (mapper.delete(row) != 1) {
            throw new ConcurrentRequestException("验证账号已被其他操作修改，请刷新后重试");
        }
    }
}
