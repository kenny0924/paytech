package com.paytech.commerce.domainservice.repository.customer;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.commerce.core.dal.dataobject.customer.ConsumerContext;
import com.paytech.commerce.core.dal.mapper.customer.ConsumerContextMapper;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.ConsumerDetails;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.ProtectedConsumer;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** 消费者表的 MyBatis 查询。 */
@Repository
@AllArgsConstructor
public class ConsumerContextRepositoryImpl implements ConsumerContextRepository {
    private final ConsumerContextMapper consumerContextMapper;

    /** 支付只需要存在性，不读取消费者保护引用。 */
    @Override
    public boolean existsByOrderId(long orderId) {
        return consumerContextMapper.existsByOrderId(orderId);
    }

    /** 只读取本次回填与版本比较所需字段。 */
    @Override
    public ConsumerContext findByOrderId(long orderId) {
        return consumerContextMapper.findByOrderId(orderId);
    }

    /** 条件写入成功后再更新原对象；失败由 Service 的本地事务整体回滚。 */
    @Override
    public void saveConsumer(ConsumerContext consumer, ConsumerDetails details, ProtectedConsumer values, Instant now) {
        var address = details.billingAddress();
        String domain = details.email().substring(details.email().lastIndexOf('@') + 1);
        boolean firstSave = consumer.getVersion() == 0;
        int rows = firstSave ? consumerContextMapper.insert(consumer, values, domain, address, now)
            : consumerContextMapper.update(consumer, values, domain, address, now);
        if (rows != 1) {
            throw new ConcurrentRequestException("消费者资料已改变，请重新保存");
        }
        consumer.setEmailVaultToken(values.emailToken());
        consumer.setConsumerProfileVaultToken(values.profileToken());
        consumer.setEmailDomain(domain);
        consumer.setBillingCountryCode(address.countryCode());
        consumer.setBillingRegionCode(address.region());
        consumer.setBillingCity(address.city());
        if (firstSave) {
            consumer.setCreateTime(now);
        }
        consumer.setVersion(consumer.getVersion() + 1);
        consumer.setUpdateTime(now);
    }
}
