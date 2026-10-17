package com.paytech.platform.support.integration.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationApi;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationScript;
import com.paytech.platform.support.integration.domain.ExternalApiConfigurationCodec;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domain.IntegrationScriptSummaryView;
import com.paytech.platform.support.integration.domain.ParameterDeclaration;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationApiRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationScriptRepository;
import com.paytech.platform.support.integration.service.processor.request.ListIntegrationChildrenRequest;

import lombok.AllArgsConstructor;

/**
 * E17 公共脚本列表：可按执行阶段筛选，分页读取后扫描本接入方原子 API 定义得到引用方。
 *
 * <p>普通列表不开显式事务，允许各查询观察相邻的已提交状态。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListIntegrationScriptsProcessor
    extends AbstractProcessor<ListIntegrationChildrenRequest, PagingResponseBody<IntegrationScriptSummaryView>> {

    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final IntegrationApiRepository integrationApis;
    private final OperationsManagementPolicyService policy;
    private final IntegrationScriptRepository integrationScripts;

    @Override
    protected PagingResponseBody<IntegrationScriptSummaryView> processRequest(ListIntegrationChildrenRequest request) {
        policy.requirePermission(request.getActor(), request.getSession(), ExternalApiPermissions.READ);
        long integrationId = children.integration(request.getIntegrationCode()).getIntegrationId();
        var paging = request.getPaging();
        var rows = integrationScripts.list(integrationId, request.getKeyword(), request.getStage(), paging.offset(),
            paging.size());
        long total = integrationScripts.count(integrationId, request.getKeyword(), request.getStage());
        var apis = rows.isEmpty() ? List.<IntegrationApi>of() : integrationApis.allByIntegration(integrationId);
        var items = rows.stream().map(row -> summary(row, apis)).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 组合单个列表项；引用方按原子 API 编码排序。 */
    IntegrationScriptSummaryView summary(IntegrationScript row, List<IntegrationApi> apis) {
        var referencedBy = apis.stream()
            .filter(api -> codec.scriptCodes(api.getDefinitionJson()).contains(row.getScriptCode()))
            .map(IntegrationApi::getApiCode).sorted().toList();
        var names = codec.parameters(row.getParameterDefinition()).stream().map(ParameterDeclaration::getName).toList();
        return new IntegrationScriptSummaryView(row.getScriptCode(), row.getScriptName(), row.getExecutionStage(),
            names, referencedBy, row.getVersion(), row.getUpdateTime());
    }
}
