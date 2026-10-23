package com.paytech.platform.support.service.processor.filestorage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.filedocument.domain.storage.FileScanPolicyDetailView;
import com.paytech.platform.support.filedocument.domainservice.service.FileScanPolicyService;
import com.paytech.platform.support.service.processor.filestorage.request.ScanPolicyQueryRequest;

import lombok.AllArgsConstructor;

/** 检查策略详情：查看或维护权限与修订、检查要求、路由读取在同一只读快照；不存在返回 404。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetScanPolicyProcessor extends AbstractProcessor<ScanPolicyQueryRequest, FileScanPolicyDetailView> {

    private final FileScanPolicyService policies;
    private final FileStorageManagementSupport support;

    @Override
    protected FileScanPolicyDetailView processRequest(ScanPolicyQueryRequest request) {
        return support.read(request.getActor(), request.getSession(), () -> policies.policy(request.getPolicyCode()));
    }
}
