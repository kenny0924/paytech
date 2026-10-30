package com.paytech.platform.support.dataquery.domainservice.repository.impl;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryKeyType;
import com.paytech.platform.support.dataquery.core.dal.mapper.QueryKeyTypeMapper;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyTypeStatus;
import com.paytech.platform.support.dataquery.domainservice.repository.QueryKeyTypeRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的键类型仓储；条件更新行数不符时报并发冲突，成功后同步原对象。 */
@Repository
@AllArgsConstructor
public class QueryKeyTypeRepositoryImpl implements QueryKeyTypeRepository {

    private final QueryKeyTypeMapper mapper;

    /** 只把编码唯一键冲突转换为“已存在”业务拒绝。 */
    @Override
    public void insert(QueryKeyType keyType) {
        try {
            if (mapper.insert(keyType) != 1) {
                throw new ErrorException("键类型插入行数异常: " + keyType.getKeyTypeCode());
            }
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_query_key_type_code")) {
                throw failure;
            }
            throw new BizCheckFailException("键类型编码已存在: " + keyType.getKeyTypeCode(), failure);
        }
    }

    /** 按主键查询。 */
    @Override
    public QueryKeyType findById(long keyTypeId) {
        return mapper.selectById(keyTypeId);
    }

    /** 按主键加排他锁读取。 */
    @Override
    public QueryKeyType findByIdForUpdate(long keyTypeId) {
        return mapper.selectByIdForUpdate(keyTypeId);
    }

    /** 空集合不发出 IN () 语句。 */
    @Override
    public List<QueryKeyType> findByCodes(Collection<String> codes) {
        return codes.isEmpty() ? List.of() : mapper.selectByCodes(codes);
    }

    /** 空集合不发出 IN () 语句。 */
    @Override
    public List<QueryKeyType> findByCodesForShare(Collection<String> codes) {
        return codes.isEmpty() ? List.of() : mapper.selectByCodesForShare(codes);
    }

    /** 全部键类型。 */
    @Override
    public List<QueryKeyType> findAll() {
        return mapper.selectAll();
    }

    /** 全部键类型加排他锁。 */
    @Override
    public List<QueryKeyType> findAllForUpdate() {
        return mapper.selectAllForUpdate();
    }

    /** 分页查询，关键字按字面匹配。 */
    @Override
    public List<QueryKeyType> findPage(QueryKeyTypeStatus status, String keyword, PagingRequestBody paging) {
        return mapper.selectPage(status, LikePatterns.escape(keyword), paging.offset(), paging.size());
    }

    /** 与分页相同条件的总数。 */
    @Override
    public long count(QueryKeyTypeStatus status, String keyword) {
        return mapper.count(status, LikePatterns.escape(keyword));
    }

    /** 条件更新成功后同步可修改字段、版本与修改人。 */
    @Override
    public void updateDefinition(QueryKeyType keyType, QueryKeyType next) {
        DataSourceRepositoryImpl.requireVersionAvailable(keyType.getVersion(), keyType.getKeyTypeCode());
        DataSourceRepositoryImpl.requireUpdated(mapper.updateDefinition(keyType, next), keyType.getKeyTypeCode());
        keyType.setKeyTypeName(next.getKeyTypeName());
        keyType.setGroupName(next.getGroupName());
        keyType.setValueType(next.getValueType());
        keyType.setDefaultRole(next.getDefaultRole());
        keyType.setEntryAllowed(next.getEntryAllowed());
        keyType.setValuePattern(next.getValuePattern());
        keyType.setInputHint(next.getInputHint());
        keyType.setSortNo(next.getSortNo());
        keyType.setDescription(next.getDescription());
        keyType.setVersion(keyType.getVersion() + 1);
        keyType.setUpdatedBy(next.getUpdatedBy());
        keyType.setUpdateTime(next.getUpdateTime());
    }

    /** 条件更新成功后同步状态、版本与修改人。 */
    @Override
    public void changeStatus(QueryKeyType keyType, QueryKeyTypeStatus status, long updatedBy, Instant updateTime) {
        DataSourceRepositoryImpl.requireVersionAvailable(keyType.getVersion(), keyType.getKeyTypeCode());
        DataSourceRepositoryImpl.requireUpdated(mapper.updateStatus(keyType, status, updatedBy, updateTime),
            keyType.getKeyTypeCode());
        keyType.setStatus(status);
        keyType.setVersion(keyType.getVersion() + 1);
        keyType.setUpdatedBy(updatedBy);
        keyType.setUpdateTime(updateTime);
    }

    /** 条件更新成功后同步顺序号、版本与修改人。 */
    @Override
    public void changeSortNo(QueryKeyType keyType, int sortNo, long updatedBy, Instant updateTime) {
        DataSourceRepositoryImpl.requireVersionAvailable(keyType.getVersion(), keyType.getKeyTypeCode());
        DataSourceRepositoryImpl.requireUpdated(mapper.updateSortNo(keyType, sortNo, updatedBy, updateTime),
            keyType.getKeyTypeCode());
        keyType.setSortNo(sortNo);
        keyType.setVersion(keyType.getVersion() + 1);
        keyType.setUpdatedBy(updatedBy);
        keyType.setUpdateTime(updateTime);
    }
}
