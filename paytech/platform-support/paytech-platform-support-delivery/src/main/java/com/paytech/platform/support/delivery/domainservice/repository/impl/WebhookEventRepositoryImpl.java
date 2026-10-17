package com.paytech.platform.support.delivery.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookEvent;
import com.paytech.platform.support.delivery.core.dal.mapper.WebhookEventMapper;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookEventRepository;

import lombok.RequiredArgsConstructor;

/** 单表访问及写入行数保护，不自行创建事务。 */
@Repository
@RequiredArgsConstructor
public class WebhookEventRepositoryImpl implements WebhookEventRepository {

    private final WebhookEventMapper mapper;

    /** 读取事件，缺失返回空。 */
    @Override
    public WebhookEvent find(long id) {
        return mapper.find(id);
    }

    /** 按来源身份读取已受理事件，缺失返回空。 */
    @Override
    public WebhookEvent findSource(long tenantId, String sourceEventKey, String sourceSystem) {
        return mapper.findSource(tenantId, sourceEventKey, sourceSystem);
    }

    /** 插入新事件；唯一冲突由 DuplicateKeyException 向上传播。 */
    @Override
    public void insert(WebhookEvent item) {
        if (mapper.insert(item) != 1) {
            throw new ErrorException("Webhook 事件插入失败");
        }
    }
}
