package com.paytech.platform.support.delivery.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationRequest;
import com.paytech.platform.support.delivery.core.dal.mapper.NotificationRequestMapper;
import com.paytech.platform.support.delivery.domain.NotificationRequestFilter;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationRequestRepository;

import lombok.RequiredArgsConstructor;

/** 单表访问及写入行数保护，不自行创建事务；只读写本库，不依赖 notification.enabled。 */
@Repository
@RequiredArgsConstructor
public class NotificationRequestRepositoryImpl implements NotificationRequestRepository {

    private final NotificationRequestMapper mapper;

    /** 读取本表的准确身份，缺失返回空。 */
    @Override
    public NotificationRequest find(long id) {
        return mapper.find(id);
    }

    /** 插入新事实；唯一约束冲突使整个受理事务回滚。 */
    @Override
    public void insert(NotificationRequest item) {
        if (mapper.insert(item) != 1)
            throw new ErrorException("通知事实插入失败");
    }

    /** 按运营筛选条件、创建时间倒序分页。 */
    @Override
    public List<NotificationRequest> page(NotificationRequestFilter filter, long offset, int size) {
        return mapper.page(filter, offset, size);
    }

    /** 与分页相同条件的总数。 */
    @Override
    public long count(NotificationRequestFilter filter) {
        return mapper.count(filter);
    }

    /** 查找来源系统内已受理的通知意图。 */
    @Override
    public NotificationRequest findSource(String sourceSystem, String sourceEventKey) {
        return mapper.findSource(sourceSystem, sourceEventKey);
    }
}
