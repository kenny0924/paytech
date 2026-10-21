package com.paytech.channel.connector.protocol.domainservice.repository.impl;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ApiField;
import com.paytech.channel.connector.protocol.core.dal.mapper.ApiFieldMapper;
import com.paytech.channel.connector.protocol.core.enums.ApiFieldDirection;
import com.paytech.channel.connector.protocol.domainservice.repository.ApiFieldRepository;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** 校验API 字段写入行数，成功后同步原对象；事务由调用方持有。 */
@Repository
@AllArgsConstructor
public class ApiFieldRepositoryImpl implements ApiFieldRepository {

    private final ApiFieldMapper mapper;

    /** 按 API 与方向批量读取字段，按顺序号排序。 */
    @Override
    public List<ApiField> byApis(Collection<Long> apiIds, Collection<ApiFieldDirection> directions) {
        if (apiIds.isEmpty() || directions.isEmpty()) {
            return List.of();
        }
        return mapper.byApis(apiIds, directions);
    }

    /** 删除某个 API 指定方向的全部字段，返回删除行数。 */
    @Override
    public int deleteByApi(long apiId, Collection<ApiFieldDirection> directions) {
        if (directions.isEmpty()) {
            return 0;
        }
        return mapper.deleteByApi(apiId, directions);
    }

    /** 批量插入字段，行数必须与输入一致。 */
    @Override
    public void insertAll(List<ApiField> rows) {
        if (rows.isEmpty()) {
            return;
        }
        try {
            if (mapper.insertAll(rows) != rows.size()) {
                throw new ConcurrentRequestException("字段保存未成功，请刷新后重试");
            }
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_api_field_path")) {
                throw failure;
            }
            throw new BizCheckFailException("同一位置下字段路径重复", failure);
        }
    }
}
