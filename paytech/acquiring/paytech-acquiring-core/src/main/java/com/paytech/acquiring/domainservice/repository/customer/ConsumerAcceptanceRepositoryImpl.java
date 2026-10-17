package com.paytech.commerce.domainservice.repository.customer;

import java.util.List;

import com.paytech.commerce.core.dal.dataobject.customer.ConsumerAcceptance;
import com.paytech.commerce.core.dal.mapper.customer.ConsumerAcceptanceMapper;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import org.springframework.stereotype.Repository;

import lombok.AllArgsConstructor;

/** 仅访问所属表，写入失败交给调用方事务回滚。 */
@Repository
@AllArgsConstructor
public class ConsumerAcceptanceRepositoryImpl implements ConsumerAcceptanceRepository {
    private final ConsumerAcceptanceMapper mapper;

    /** 读取准确合同的不可变接受记录，不拼接网络原值。 */
    @Override
    public List<ConsumerAcceptance> findByContractId(long contractId) {
        return mapper.findByContractId(contractId);
    }

    /** 保存一条不可变接受记录，参与调用方事务。 */
    @Override
    public void insert(ConsumerAcceptance acceptance) {
        if (mapper.insert(acceptance) != 1) {
            throw new ConcurrentRequestException("协议接受记录保存失败");
        }
    }
    /** 是否已受理过支付；用于编辑和取消门禁。 */
    @Override
    public boolean existsByOrderId(long orderId) {
        return mapper.existsByOrderId(orderId);
    }
}

