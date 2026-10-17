package com.paytech.platform.support.delivery.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationTemplate;
import com.paytech.platform.support.delivery.core.dal.mapper.NotificationTemplateMapper;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationTemplateRepository;

import lombok.RequiredArgsConstructor;

/** 单表访问及写入行数保护，不自行创建事务；只读写本库，不依赖 notification.enabled。 */
@Repository
@RequiredArgsConstructor
public class NotificationTemplateRepositoryImpl implements NotificationTemplateRepository {

    private final NotificationTemplateMapper mapper;

    /** 读取本表的准确身份，缺失返回空。 */
    @Override
    public NotificationTemplate find(long id) {
        return mapper.find(id);
    }

    /** 按唯一模板编码读取当前定义。 */
    @Override
    public NotificationTemplate findCode(String code) {
        return mapper.findCode(code);
    }

    /** 按身份批量读取；空集合直接返回空列表。 */
    @Override
    public List<NotificationTemplate> findIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return mapper.findIds(ids);
    }

    /** 按编码升序分页，keyword 为已转义的 LIKE 模式，可空。 */
    @Override
    public List<NotificationTemplate> page(String keyword, NotificationConfigurationStatus status, long offset,
        int size) {
        return mapper.page(keyword, status, offset, size);
    }

    /** 与分页相同条件的总数。 */
    @Override
    public long count(String keyword, NotificationConfigurationStatus status) {
        return mapper.count(keyword, status);
    }

    /** 插入新模板；编码唯一冲突由调用方事务回滚。 */
    @Override
    public void insert(NotificationTemplate item) {
        if (mapper.insert(item) != 1) {
            throw new ErrorException("通知模板插入失败");
        }
    }

    /** 按原版本和原状态切换启停；成功后同步原对象。 */
    @Override
    public void changeStatus(NotificationTemplate item, NotificationConfigurationStatus status, Instant now) {
        if (mapper.changeStatus(item, status, now) != 1) {
            throw new ConcurrentRequestException("通知模板已被其他操作修改，请刷新后重试");
        }
        item.setStatus(status);
        item.setVersion(item.getVersion() + 1);
        item.setUpdateTime(now);
    }
}
