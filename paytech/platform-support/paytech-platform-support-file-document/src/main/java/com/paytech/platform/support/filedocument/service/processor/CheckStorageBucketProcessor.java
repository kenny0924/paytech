package com.paytech.platform.support.filedocument.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.enums.FileStorageCheckResult;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageInstance;
import com.paytech.platform.support.filedocument.domainservice.repository.FileStorageBucketRepository;
import com.paytech.platform.support.filedocument.domainservice.repository.FileStorageInstanceRepository;
import com.paytech.platform.support.filedocument.integration.storage.FileObjectStorage;
import com.paytech.platform.support.filedocument.integration.storage.FileStorageRegistry;
import com.paytech.platform.support.filedocument.service.processor.request.CheckStorageBucketRequest;

import lombok.AllArgsConstructor;

/**
 * 模块公开入口：用实例当前登记的地址与密钥检查一个存储桶，不写库
 * <li>调用方须在数据库事务外调用，并负责鉴权与回写结果；实例为 DRAFT 也可检查。</li>
 * <li>实例或存储桶不存在返回 404；Vault 不可用或密钥无效返回 503 且不发起存储调用；存储侧问题作为检查结果返回。</li>
 */
@Component
@AllArgsConstructor
public class CheckStorageBucketProcessor extends AbstractProcessor<CheckStorageBucketRequest, FileStorageCheckResult> {

    private final FileStorageRegistry registry;
    private final FileStorageBucketRepository buckets;
    private final FileStorageInstanceRepository instances;

    @Override
    protected FileStorageCheckResult processRequest(CheckStorageBucketRequest request) {
        // 读取当前登记的实例与存储桶
        FileStorageInstance instance = instances.findByCode(request.getInstanceCode());
        if (instance == null || buckets.findByName(instance.getInstanceId(), request.getBucketName()) == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }

        // 事务外构建本次客户端并检查
        try (FileObjectStorage storage = registry.connect(instance)) {
            return storage.checkBucket(request.getBucketName());
        }
    }
}
