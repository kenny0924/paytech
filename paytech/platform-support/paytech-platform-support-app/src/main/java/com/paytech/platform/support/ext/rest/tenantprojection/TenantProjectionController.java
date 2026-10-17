package com.paytech.platform.support.ext.rest.tenantprojection;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.accesscontrol.service.processor.GetTenantProjectionProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.ProjectTenantStatusProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.ProvisionTenantProcessor;
import com.paytech.platform.support.api.tenantprojection.TenantProjectionApi;
import com.paytech.platform.support.api.tenantprojection.request.ProvisionRequestBody;
import com.paytech.platform.support.api.tenantprojection.request.StatusRequestBody;
import com.paytech.platform.support.api.tenantprojection.response.ProjectionResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;

import lombok.AllArgsConstructor;

/** CCT 内部适配器，不接受浏览器直接开通或权限选择。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "tenant.projection.enabled", havingValue = "true")
public class TenantProjectionController implements TenantProjectionApi {
    private final ProvisionTenantProcessor provisionTenantProcessor;
    private final TenantProjectionConverter tenantProjectionConverter;
    private final GetTenantProjectionProcessor getTenantProjectionProcessor;
    private final ProjectTenantStatusProcessor projectTenantStatusProcessor;

    @Override
    @Audit(action = "CCT_TENANT_PROVISION", resourceType = "ACCESS_TENANT")
    public ProjectionResponseBody provision(ProvisionRequestBody request) {
        return tenantProjectionConverter
            .response(provisionTenantProcessor.process(tenantProjectionConverter.provision(request)));
    }

    @Override
    @Audit(action = "CCT_TENANT_STATUS_PROJECT", resourceType = "ACCESS_TENANT")
    public ProjectionResponseBody projectStatus(StatusRequestBody request) {
        return tenantProjectionConverter
            .response(projectTenantStatusProcessor.process(tenantProjectionConverter.status(request)));
    }

    @Override
    public ProjectionResponseBody projection(String tenantType, String ownerRefId) {
        return tenantProjectionConverter
            .response(getTenantProjectionProcessor.process(tenantProjectionConverter.query(tenantType, ownerRefId)));
    }
}
