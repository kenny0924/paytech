package com.paytech.commerce.domainservice.repository.customer;

import org.springframework.stereotype.Repository;

import com.paytech.commerce.core.dal.dataobject.customer.ConsumerContract;
import com.paytech.commerce.core.dal.dataobject.customer.FrozenCheckoutRecord;
import com.paytech.commerce.core.dal.mapper.customer.ConsumerContractMapper;
import com.paytech.commerce.core.dal.mapper.customer.CustomerCheckoutMapper;
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

    /** 查找本订单已冻结且法律关系完整的合同。 */
    @Override
    public ConsumerContract findByOrderId(long orderId) {
        return mapper.findByOrderId(orderId);
    }
}

