package com.paytech.channel.connector.protocol.service.processor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelImplementation;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.ValidationIssue;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelImplementationRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.StandardApiRepository;
import com.paytech.channel.connector.protocol.service.processor.request.ValidateImplementationRequest;

import lombok.AllArgsConstructor;

/**
 * E28 只校验渠道标准 API 实现：与保存使用同一套流程规则，问题以列表返回而不抛错，不加锁、不写库。
 *
 * <p>标准 API 不存在时作为问题返回；未带版本时按新建判断是否已实现，带版本时与当前版本不一致只给 WARNING。
 */
@Component
@AllArgsConstructor
public class ValidateImplementationProcessor
    extends AbstractProcessor<ValidateImplementationRequest, List<ValidationIssue>> {

    private final IntegrationChildSupport children;
    private final ImplementationSupport flows;
    private final StandardApiRepository standardApis;
    private final ExternalApiTransactions transactions;
    private final ChannelImplementationRepository implementations;

    @Override
    protected List<ValidationIssue> processRequest(ValidateImplementationRequest request) {
        return transactions.query(() -> {
            long channelId = children.channel(request.getIntegrationCode()).getChannelId();
            var standardApi = standardApis.byCode(request.getStandardApiCode());
            if (standardApi == null) {
                return List.of(new ValidationIssue("STANDARD_API_NOT_FOUND", "standardApiCode", "标准 API 不存在"));
            }
            var issues = new ArrayList<ValidationIssue>(flows.check(standardApi, channelId,
                request.getFlowDefinition()).getIssues());
            issues.addAll(versionIssues(implementations.byKey(channelId, standardApi.getStandardApiId()),
                request.getExpectedVersion()));
            return issues;
        });
    }

    /** 新建时已实现为错误；修改时版本落后只提示。 */
    List<ValidationIssue> versionIssues(ChannelImplementation existing, Integer expectedVersion) {
        if (expectedVersion == null && existing != null) {
            return List.of(new ValidationIssue("IMPLEMENTATION_EXISTS", "standardApiCode", "该渠道已实现此标准 API"));
        }
        if (expectedVersion != null && existing != null && existing.getVersion() != expectedVersion) {
            return List.of(new ValidationIssue(ValidationIssue.WARNING, "VERSION_STALE", "expectedVersion",
                "配置已被修改，当前版本为 " + existing.getVersion()));
        }
        return List.of();
    }
}
