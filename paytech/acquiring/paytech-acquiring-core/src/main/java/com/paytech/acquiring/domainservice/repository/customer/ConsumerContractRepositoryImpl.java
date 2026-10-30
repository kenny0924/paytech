package com.paytech.acquiring.domainservice.repository.customer;

import org.springframework.stereotype.Repository;

import com.paytech.acquiring.core.dal.dataobject.customer.ConsumerContract;
import com.paytech.acquiring.core.dal.dataobject.customer.FrozenCheckoutRecord;
import com.paytech.acquiring.core.dal.mapper.customer.ConsumerContractMapper;
import com.paytech.acquiring.core.dal.mapper.customer.CustomerCheckoutMapper;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 仅访问所属表，写入失败交给调用方事务回滚。 */
@Repository
@AllArgsConstructor
public class ConsumerContractRepositoryImpl implements ConsumerContractRepository {
    private final ConsumerContractMapper mapper;
    private final CustomerCheckoutMapper preparationMapper;

    /** 插入本表事实，唯一约束失败由外层事务整体回滚。 */
    @Override
    public void insert(FrozenCheckoutRecord row) {
        if (preparationMapper.insertContract(row) != 1) {
            throw new ErrorException("准备事实未成功保存");
        }
    }

    /** 查找本订单当前（最新呈现）且法律关系完整的合同；报价变化后每个税务计算一份，取最新一份。 */
    @Override
    public ConsumerContract findByOrderId(long orderId) {
        return mapper.findByOrderId(orderId);
    }
}

