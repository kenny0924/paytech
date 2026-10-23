package com.paytech.platform.support.service.processor.filestorage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.filedocument.domainservice.service.FileScanPolicyService;
import com.paytech.platform.support.filedocument.integration.scanning.FormatFileScanner;
import com.paytech.platform.support.service.processor.filestorage.request.ScanPolicyQueryRequest;
import com.paytech.platform.support.service.processor.filestorage.response.ScanPolicyCatalog;

import lombok.AllArgsConstructor;

/** 检查策略列表：查看或维护权限与修订、路由读取在同一只读快照；附格式检查器能识别的类型。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListScanPoliciesProcessor extends AbstractProcessor<ScanPolicyQueryRequest, ScanPolicyCatalog> {

    private final FileScanPolicyService policies;
    private final FileStorageManagementSupport support;

    @Override
    protected ScanPolicyCatalog processRequest(ScanPolicyQueryRequest request) {
        return support.read(request.getActor(), request.getSession(),
            () -> new ScanPolicyCatalog(policies.listPolicies(), FormatFileScanner.SUPPORTED_MIME_TYPES));
    }
}
