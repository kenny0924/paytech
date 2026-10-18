package com.paytech.platform.support.integration.domainservice.repository.impl;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import com.paytech.platform.support.integration.core.dal.mapper.IntegrationMapper;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** 校验外部接入方写入行数，成功后同步原对象；事务由调用方持有。 */
@Repository
@AllArgsConstructor
public class IntegrationRepositoryImpl implements IntegrationRepository {

    private final IntegrationMapper mapper;

    /** 按不可变编码读取接入方。 */
    @Override
    public Integration byCode(String code) {
        return mapper.byCode(code);
    }

    /** 在调用方事务中锁定接入方行，供版本校验及条件更新。 */
    @Override
    public Integration lock(String code) {
        return mapper.lock(code);
    }

    /** 按标识批量读取接入方，按编码排序。 */
    @Override
    public List<Integration> byIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return mapper.byIds(ids);
    }

    /** 分页查询，按编码与标识稳定排序。 */
    @Override
    public List<Integration> list(String keyword, long offset, int size) {
        return mapper.list(keyword, offset, size);
    }

    /** 使用与分页相同的筛选统计总数。 */
    @Override
    public long count(String keyword) {
        return mapper.count(keyword);
    }

    /** 插入新接入方，编码由唯一索引保护。 */
    @Override
    public void insert(Integration row) {
        try {
            if (mapper.insert(row) != 1) {
                throw new ConcurrentRequestException("外部接入方创建未成功，请刷新后重试");
            }
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_integration_code")) {
                throw failure;
            }
            throw new BizCheckFailException("接入方编码已经存在", failure);
        }
    }

    /** 按原版本更新名称、说明与出站地址，并递增版本。 */
    @Override
    public void update(Integration row, String integrationName, String description, String endpoints, long updatedBy, Instant updateTime) {
        int previousVersion = row.getVersion();
        if (previousVersion == Integer.MAX_VALUE || mapper.update(row, integrationName, description, endpoints, updatedBy, updateTime) != 1) {
            throw new ConcurrentRequestException("外部接入方已被其他操作修改，请刷新后重试");
        }
        row.setIntegrationName(integrationName);
        row.setDescription(description);
        row.setEndpoints(endpoints);
        row.setVersion(previousVersion + 1);
        row.setUpdatedBy(updatedBy);
        row.setUpdateTime(updateTime);
    }

    /** 按原版本删除接入方。 */
    @Override
    public void delete(Integration row) {
        if (mapper.delete(row) != 1) {
            throw new ConcurrentRequestException("外部接入方已被其他操作修改，请刷新后重试");
        }
    }
}
