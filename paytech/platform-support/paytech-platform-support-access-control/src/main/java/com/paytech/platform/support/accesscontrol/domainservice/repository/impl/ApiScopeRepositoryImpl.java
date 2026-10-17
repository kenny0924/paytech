package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ApiScope;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.ApiScopeMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ApiScopeRepository;

/** 校验目录写入行数，成功后同步原对象；事务由调用方持有。 */
@Repository
@lombok.AllArgsConstructor
public class ApiScopeRepositoryImpl implements ApiScopeRepository {
    private final ApiScopeMapper mapper;

    /** 按不可变编码读取 Scope 目录定义。 */
    @Override
    public ApiScope scope(String code) {
        return mapper.scope(code);
    }

    /** 在调用方本库事务中锁定目标行，供版本校验及条件更新使用。 */
    @Override
    public ApiScope lock(String code) {
        return mapper.lock(code);
    }

    /** 分页查询 Scope 信息，保留服务端排序及总数。 */
    @Override
    public java.util.List<ApiScope> scopes(String keyword, long offset, int size) {
        return mapper.scopes(keyword, offset, size);
    }

    /** 使用与列表相同的筛选条件统计总数。 */
    @Override
    public long count(String keyword) {
        return mapper.count(keyword);
    }

    /** 插入新 Scope 定义，由唯一索引保护编码不重复。 */
    @Override
    public void insert(ApiScope row) {
        try {
            if (mapper.insert(row) != 1) {
                throw new ConcurrentRequestException("Scope 创建未成功，请刷新后重试");
            }
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_api_scope_code")) {
                throw failure;
            }
            throw new BizCheckFailException("Scope 编码已经存在", failure);
        }
    }

    /** 按预期版本更新 Scope 名称和说明，编码保持不变。 */
    @Override
    public void update(ApiScope row, String displayName, String description, Instant updateTime) {
        int previousVersion = row.getVersion();
        if (previousVersion == Integer.MAX_VALUE || mapper.update(row, displayName, description, updateTime) != 1) {
            throw new ConcurrentRequestException("Scope 已被其他操作修改，请刷新后重试");
        }
        row.setDisplayName(displayName);
        row.setDescription(description);
        row.setVersion(previousVersion + 1);
        row.setUpdateTime(updateTime);
    }
}
