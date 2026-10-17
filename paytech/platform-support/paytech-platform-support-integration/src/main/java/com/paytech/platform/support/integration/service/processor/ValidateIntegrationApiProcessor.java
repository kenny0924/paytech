package com.paytech.platform.support.integration.service.processor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domain.ValidationIssue;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationApiRepository;
import com.paytech.platform.support.integration.service.processor.request.ValidateIntegrationApiRequest;

import lombok.AllArgsConstructor;

/**
 * E16 只校验原子 API 配置：与保存使用同一套字段与定义规则，问题以列表返回而不抛错，不加锁、不写库。
 *
 * <p>未带版本时按新建判断编码是否已存在；带版本时与当前版本不一致只给 WARNING。脚本与地址在同一只读快照中读取。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ValidateIntegrationApiProcessor
    extends AbstractProcessor<ValidateIntegrationApiRequest, List<ValidationIssue>> {

    private final IntegrationChildSupport children;
    private final ExternalApiTransactions transactions;
    private final IntegrationApiRepository integrationApis;
    private final OperationsManagementPolicyService policy;

    @Override
    protected List<ValidationIssue> processRequest(ValidateIntegrationApiRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), ExternalApiPermissions.MANAGE);
            var integration = children.integration(request.getIntegrationCode());
            var issues = new ArrayList<ValidationIssue>(children.check(integration, request.getRequestFields(),
                request.getResponseFields(), request.getDefinition(),
                children.scriptSignatures(integration.getIntegrationId())).getIssues());
            var existing = integrationApis.byCode(integration.getIntegrationId(), request.getCode());
            if (request.getExpectedVersion() == null && existing != null) {
                issues.add(new ValidationIssue("API_CODE_EXISTS", "apiCode", "原子 API 编码已经存在"));
            } else if (request.getExpectedVersion() != null && existing != null
                && existing.getVersion() != request.getExpectedVersion()) {
                issues.add(new ValidationIssue(ValidationIssue.WARNING, "VERSION_STALE", "expectedVersion",
                    "配置已被修改，当前版本为 " + existing.getVersion()));
            }
            return issues;
        });
    }
}
