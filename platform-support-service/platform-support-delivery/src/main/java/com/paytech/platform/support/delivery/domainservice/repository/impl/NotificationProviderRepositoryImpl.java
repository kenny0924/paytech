package com.paytech.platform.support.delivery.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationProvider;
import com.paytech.platform.support.delivery.core.dal.mapper.NotificationProviderMapper;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationProviderRepository;

import lombok.RequiredArgsConstructor;

/** 单表访问及写入行数保护，不自行创建事务；只读写本库，不依赖 notification.enabled。 */
@Repository
@RequiredArgsConstructor
public class NotificationProviderRepositoryImpl implements NotificationProviderRepository {

    private final NotificationProviderMapper mapper;

    /** 读取本表的准确身份，缺失返回空。 */
    @Override
    public NotificationProvider find(long id) {
        return mapper.find(id);
    }

    /** 按唯一账号编码读取，缺失返回空。 */
    @Override
    public NotificationProvider findCode(String code) {
        return mapper.findCode(code);
    }

    /** 按身份批量读取；空集合直接返回空列表。 */
    @Override
    public List<NotificationProvider> findIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return mapper.findIds(ids);
    }

    /** 按编码升序分页。 */
    @Override
    public List<NotificationProvider> page(long offset, int size) {
        return mapper.page(offset, size);
    }

    /** 账号总数。 */
    @Override
    public long count() {
        return mapper.count();
    }

    /** 按原版本和原状态切换启停；成功后同步原对象。 */
    @Override
    public void changeStatus(NotificationProvider item, NotificationConfigurationStatus status, Instant now) {
        if (mapper.changeStatus(item, status, now) != 1) {
            throw new ConcurrentRequestException("发送账号已被其他操作修改，请刷新后重试");
        }
        item.setStatus(status);
        item.setVersion(item.getVersion() + 1);
        item.setUpdateTime(now);
    }
}
