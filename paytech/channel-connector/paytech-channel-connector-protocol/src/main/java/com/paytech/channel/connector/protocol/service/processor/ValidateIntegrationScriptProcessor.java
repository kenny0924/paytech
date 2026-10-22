package com.paytech.channel.connector.protocol.service.processor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.ValidationIssue;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelScriptRepository;
import com.paytech.channel.connector.protocol.domainservice.validator.IntegrationScriptValidator;
import com.paytech.channel.connector.protocol.service.processor.request.ValidateIntegrationScriptRequest;

import lombok.AllArgsConstructor;

/**
 * E22 只校验公共脚本：参数与源码规则，以及按新签名会失效的引用方原子 API；问题以列表返回，不加锁、不写库。
 *
 * <p>未带版本时按新建判断编码是否已存在；带版本时与当前版本不一致只给 WARNING。
 */
@Component
@AllArgsConstructor
public class ValidateIntegrationScriptProcessor
    extends AbstractProcessor<ValidateIntegrationScriptRequest, List<ValidationIssue>> {

    private final IntegrationChildSupport children;
    private final IntegrationScriptValidator validator;
    private final ExternalApiTransactions transactions;
    private final ChannelScriptRepository channelScripts;

    @Override
    protected List<ValidationIssue> processRequest(ValidateIntegrationScriptRequest request) {
        return transactions.query(() -> {
            var integration = children.channel(request.getIntegrationCode());
            var issues = new ArrayList<>(validator.validate(request.getParameters(), request.getSourceCode()));
            var existing = channelScripts.byCode(integration.getChannelId(), request.getCode());
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
