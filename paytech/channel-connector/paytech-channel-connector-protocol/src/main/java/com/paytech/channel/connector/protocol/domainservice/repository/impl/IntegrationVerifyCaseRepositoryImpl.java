package com.paytech.channel.connector.protocol.domainservice.repository.impl;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyCase;
import com.paytech.channel.connector.protocol.core.dal.mapper.IntegrationVerifyCaseMapper;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationVerifyCaseRepository;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** 校验验证用例写入行数，成功后同步原对象；事务由调用方持有。 */
@Repository
@AllArgsConstructor
public class IntegrationVerifyCaseRepositoryImpl implements IntegrationVerifyCaseRepository {

    private final IntegrationVerifyCaseMapper mapper;

    /** 按接入方与不可变编码读取用例。 */
    @Override
    public IntegrationVerifyCase byCode(long integrationId, String code) {
        return mapper.byCode(integrationId, code);
    }

    /** 在调用方事务中锁定用例行，供版本校验及条件更新。 */
    @Override
    public IntegrationVerifyCase lock(long integrationId, String code) {
        return mapper.lock(integrationId, code);
    }

    /** 分页查询，按编码与标识稳定排序。 */
    @Override
    public List<IntegrationVerifyCase> list(long integrationId, String keyword, long offset, int size) {
        return mapper.list(integrationId, keyword, offset, size);
    }

    /** 使用与分页相同的筛选统计总数。 */
    @Override
    public long count(long integrationId, String keyword) {
        return mapper.count(integrationId, keyword);
    }

    /** 统计引用某验证账号的用例数量。 */
    @Override
    public long countByAccount(long verifyAccountId) {
        return mapper.countByAccount(verifyAccountId);
    }

    /** 插入新用例，接入方内编码由唯一索引保护。 */
    @Override
    public void insert(IntegrationVerifyCase row) {
        try {
            if (mapper.insert(row) != 1) {
                throw new ConcurrentRequestException("验证用例创建未成功，请刷新后重试");
            }
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_verify_case_code")) {
                throw failure;
            }
            throw new BizCheckFailException("验证用例编码已经存在", failure);
        }
    }

    /** 按原版本更新用例内容，并递增版本。 */
    @Override
    public void update(IntegrationVerifyCase row, String caseName, String description, long verifyAccountId, String stepsJson, long updatedBy, Instant updateTime) {
        int previousVersion = row.getVersion();
        if (previousVersion == Integer.MAX_VALUE || mapper.update(row, caseName, description, verifyAccountId, stepsJson, updatedBy, updateTime) != 1) {
            throw new ConcurrentRequestException("验证用例已被其他操作修改，请刷新后重试");
        }
        row.setCaseName(caseName);
        row.setDescription(description);
        row.setVerifyAccountId(verifyAccountId);
        row.setStepsJson(stepsJson);
        row.setVersion(previousVersion + 1);
        row.setUpdatedBy(updatedBy);
        row.setUpdateTime(updateTime);
    }

    /** 按原版本删除用例；运行记录保留。 */
    @Override
    public void delete(IntegrationVerifyCase row) {
        if (mapper.delete(row) != 1) {
            throw new ConcurrentRequestException("验证用例已被其他操作修改，请刷新后重试");
        }
    }
}
