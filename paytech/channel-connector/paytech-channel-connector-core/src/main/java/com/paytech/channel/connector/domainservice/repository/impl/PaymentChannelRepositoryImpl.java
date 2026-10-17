package com.paytech.payment.channel.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.payment.channel.core.dal.dataobject.PaymentChannel;
import com.paytech.payment.channel.core.dal.mapper.PaymentChannelMapper;
import com.paytech.payment.channel.domainservice.repository.PaymentChannelRepository;

import lombok.AllArgsConstructor;

/** PaymentChannel 仓储不建立事务、不解释跨表资格。 */
@Repository
@AllArgsConstructor
public class PaymentChannelRepositoryImpl implements PaymentChannelRepository {
    /** 本表 SQL 映射。 */
    private final PaymentChannelMapper mapper;

    /** 读取本表范围内的配置。 */
    @Override
    public List<PaymentChannel> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.findByIds(ids);
    }

    /** 读取指定记录，不把缺失解释为默认配置。 */
    @Override
    public PaymentChannel findById(long id) {
        return mapper.findById(id);
    }
}
