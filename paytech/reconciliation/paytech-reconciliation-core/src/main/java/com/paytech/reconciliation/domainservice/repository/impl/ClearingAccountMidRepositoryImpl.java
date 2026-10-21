package com.paytech.reconciliation.domainservice.repository.impl;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccountMid;
import com.paytech.reconciliation.core.dal.mapper.ClearingAccountMidMapper;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountMidRepository;

import lombok.AllArgsConstructor;

/** ClearingAccountMidRepository 的 MyBatis 实现：条件更新先确认行数，成功后才同步传入对象。 */
@Repository
@AllArgsConstructor
public class ClearingAccountMidRepositoryImpl implements ClearingAccountMidRepository {
    private final ClearingAccountMidMapper mapper;

    /** 插入新记录；业务唯一键冲突转为并发冲突（409），调用方应先按业务键查询。 */
    @Override
    public void insert(ClearingAccountMid record) {
        try {
            mapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("MID 与币种已归属其他清算账户: " + record.getMidId() + "/" + record.getCurrency(), e);
        }
    }

    /** 摘除 MID：删除本行；行数不为 1 抛并发冲突。 */
    @Override
    public void delete(ClearingAccountMid record) {
        if (mapper.deleteById(record.getId()) != 1) {
            throw new ConcurrentRequestException("清算账户 MID 已被摘除: " + record.getMidId());
        }
    }

    /** 按 MID + 币种读取归属（解析接口与重复归属检查）。 */
    @Override
    public ClearingAccountMid findByMidAndCurrency(Long midId, String currency) {
        return mapper.selectByMidAndCurrency(midId, currency);
    }

    /** 读取一个清算账户的全部 MID，按主键升序。 */
    @Override
    public List<ClearingAccountMid> listByClearingAccount(Long clearingAccountId) {
        return mapper.selectByClearingAccount(clearingAccountId);
    }

    /** 按 MID 集合读取归属行（未归属 MID 计算）；空集合不查询。 */
    @Override
    public List<ClearingAccountMid> listByMidIds(List<Long> midIds) {
        return midIds.isEmpty() ? List.of() : mapper.selectByMidIds(midIds);
    }
}
