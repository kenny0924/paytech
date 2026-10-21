package com.paytech.channel.connector.protocol.domainservice.repository.impl;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.core.dal.mapper.StandardApiMapper;
import com.paytech.channel.connector.protocol.domainservice.repository.StandardApiRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** 校验标准 API写入行数，成功后同步原对象；事务由调用方持有。 */
@Repository
@AllArgsConstructor
public class StandardApiRepositoryImpl implements StandardApiRepository {

    private final StandardApiMapper mapper;

    /** 按不可变编码读取标准 API。 */
    @Override
    public StandardApi byCode(String code) {
        return mapper.byCode(code);
    }

    /** 在调用方事务中锁定标准 API 行，供版本校验及条件更新。 */
    @Override
    public StandardApi lock(String code) {
        return mapper.lock(code);
    }

    /** 按标识批量读取标准 API，按编码排序。 */
    @Override
    public List<StandardApi> byIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return mapper.byIds(ids);
    }

    /** 分页查询，按编码与标识稳定排序。 */
    @Override
    public List<StandardApi> list(String keyword, long offset, int size) {
        return mapper.list(keyword, offset, size);
    }

    /** 使用与分页相同的筛选统计总数。 */
    @Override
    public long count(String keyword) {
        return mapper.count(keyword);
    }

    /** 插入新标准 API，编码由唯一索引保护。 */
    @Override
    public void insert(StandardApi row) {
        try {
            if (mapper.insert(row) != 1) {
                throw new ConcurrentRequestException("标准 API创建未成功，请刷新后重试");
            }
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_standard_api_code")) {
                throw failure;
            }
            throw new BizCheckFailException("标准 API 编码已经存在", failure);
        }
    }

    /** 按原版本更新名称与说明，并递增版本。 */
    @Override
    public void update(StandardApi row, String standardApiName, String description, long updatedBy, Instant updateTime) {
        int previousVersion = row.getVersion();
        if (previousVersion == Integer.MAX_VALUE || mapper.update(row, standardApiName, description, updatedBy, updateTime) != 1) {
            throw new ConcurrentRequestException("标准 API已被其他操作修改，请刷新后重试");
        }
        row.setStandardApiName(standardApiName);
        row.setDescription(description);
        row.setVersion(previousVersion + 1);
        row.setUpdatedBy(updatedBy);
        row.setUpdateTime(updateTime);
    }

    /** 按原版本删除标准 API。 */
    @Override
    public void delete(StandardApi row) {
        if (mapper.delete(row) != 1) {
            throw new ConcurrentRequestException("标准 API已被其他操作修改，请刷新后重试");
        }
    }
}
