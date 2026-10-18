package com.paytech.platform.support.integration.service.processor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domain.ValidationIssue;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationScriptRepository;
import com.paytech.platform.support.integration.domainservice.validator.IntegrationScriptValidator;
import com.paytech.platform.support.integration.service.processor.request.ValidateIntegrationScriptRequest;

import lombok.AllArgsConstructor;

/**
 * E22 只校验公共脚本：参数与源码规则，以及按新签名会失效的引用方原子 API；问题以列表返回，不加锁、不写库。
 *
 * <p>未带版本时按新建判断编码是否已存在；带版本时与当前版本不一致只给 WARNING。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ValidateIntegrationScriptProcessor
    extends AbstractProcessor<ValidateIntegrationScriptRequest, List<ValidationIssue>> {

    private final IntegrationChildSupport children;
    private final IntegrationScriptValidator validator;
    private final ExternalApiTransactions transactions;
    private final OperationsManagementPolicyService policy;
    private final IntegrationScriptRepository integrationScripts;

    @Override
    protected List<ValidationIssue> processRequest(ValidateIntegrationScriptRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), ExternalApiPermissions.MANAGE);
            var integration = children.integration(request.getIntegrationCode());
            var issues = new ArrayList<>(validator.validate(request.getParameters(), request.getSourceCode()));
            var existing = integrationScripts.byCode(integration.getIntegrationId(), request.getCode());
            if (request.getExpectedVersion() == null && existing != null) {
                issues.add(new ValidationIssue("SCRIPT_CODE_EXISTS", "scriptCode", "公共脚本编码已经存在"));
            } else if (request.getExpectedVersion() != null && existing != null
                && existing.getVersion() != request.getExpectedVersion()) {
                issues.add(new ValidationIssue(ValidationIssue.WARNING, "VERSION_STALE", "expectedVersion",
                    "配置已被修改，当前版本为 " + existing.getVersion()));
            }
            children.brokenScriptUsers(integration, request.getCode(), request.getStage(), request.getParameters()).forEach(code -> issues.add(new ValidationIssue("SCRIPT_USER_BROKEN",
                "executionStage", "修改后原子 API " + code + " 的脚本引用不再有效")));
            return issues;
        });
    }
}
