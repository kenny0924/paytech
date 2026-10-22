package com.paytech.platform.support.service.processor.filestorage;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageRouteView;
import com.paytech.platform.support.filedocument.domainservice.service.FileStorageManagementService;
import com.paytech.platform.support.service.processor.filestorage.request.ListStorageRequest;

import lombok.AllArgsConstructor;

/** 写入路由列表：查看或维护权限与三表读取在同一只读快照。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListStorageRoutesProcessor extends AbstractProcessor<ListStorageRequest, List<FileStorageRouteView>> {

    private final FileStorageManagementSupport support;
    private final FileStorageManagementService management;

    @Override
    protected List<FileStorageRouteView> processRequest(ListStorageRequest request) {
        return support.read(request.getActor(), request.getSession(), management::listRoutes);
    }
}
