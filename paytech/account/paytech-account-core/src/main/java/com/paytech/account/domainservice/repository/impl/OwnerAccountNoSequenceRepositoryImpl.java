package com.paytech.account.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.account.core.dal.dataobject.OwnerAccountNoSequence;
import com.paytech.account.core.dal.mapper.OwnerAccountNoSequenceMapper;
import com.paytech.account.domainservice.repository.OwnerAccountNoSequenceRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** OwnerAccountNoSequenceRepository 的 MyBatis 实现：INSERT … ON DUPLICATE KEY UPDATE 后在同一连接读取 LAST_INSERT_ID。 */
@Repository
@AllArgsConstructor
public class OwnerAccountNoSequenceRepositoryImpl implements OwnerAccountNoSequenceRepository {
    private final OwnerAccountNoSequenceMapper mapper;

    /** 原子递增后读取本连接的 LAST_INSERT_ID；插入影响 1 行、递增影响 2 行，其他结果为序列行状态异常。 */
    @Override
    public long allocate(OwnerAccountNoSequence sequence) {
        int rows = mapper.upsertNext(sequence);
        if (rows != 1 && rows != 2) {
            throw new ErrorException("账户号序列分配失败: " + sequence.getEntityType().getCode() + "/"
                + sequence.getEntityId() + "/" + sequence.getCurrency() + "/" + sequence.getAccountCode());
        }
        long value = mapper.selectLastInsertId();
        sequence.setLastValue(value);
        return value;
    }
}
