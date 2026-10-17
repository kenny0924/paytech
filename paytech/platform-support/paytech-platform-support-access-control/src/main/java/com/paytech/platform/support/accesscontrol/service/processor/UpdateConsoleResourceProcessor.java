package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.ConsoleResourceManagementProperties;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ConsoleResource;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.*;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ConfigurationCatalogRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ConsoleResourceRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.ConsoleResourceManagementService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.UpdateConsoleResourceRequest;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 整棵 Operations 树协调写入，保留数据库 SERIALIZABLE、唯一约束及版本保护。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateConsoleResourceProcessor
    extends AbstractLockProcessor<UpdateConsoleResourceRequest, ConfigurationMutation> {
    private final Clock clock;
    private final ConsoleResourceRepository repository;
    private final ConfigurationCatalogRepository catalog;
    private final ConsoleResourceManagementService resources;
    private final OperationsManagementPolicyService policy;
    private final ConsoleResourcePolicy resourcePolicy;
    private final OperationsAuditService audit;
    private final PlatformTransactionManager transactionManager;
    private final ConsoleResourceManagementProperties properties;

    /** 三个写入口共用树资源，模板在事务外获取并在事务完成后释放。 */
    @Override
    protected LockCommand lockCommand(UpdateConsoleResourceRequest request) {
        return LockCommand.tryLock("console-resource:operations",
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** SERIALIZABLE 保护父子范围及管理保护闭包，禁止外部调用进入事务。 */
    @Override
    protected ConfigurationMutation processWithinLock(UpdateConsoleResourceRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        transaction.setTimeout(15);
        try {
            return transaction.execute(status -> execute(request));
        } catch (ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("配置发生并发修改，请刷新后重试", failure);
        }
    }

    /** 当前授权及候选资格通过后构造完整 Context，本地保存成功才记录变化事实。 */
    ConfigurationMutation execute(UpdateConsoleResourceRequest request) {
        audit.observeTransaction();
        var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(), null, null,
            false, "CONSOLE_RESOURCE_MANAGE");
        var permission = catalog.lockPermission(request.getPermissionId(), false);
        var current = requireResource(request.getResourceCode(), request.getExpectedVersion());
        resourcePolicy.requireBinding(permission, current.getPermissionId());
        resourcePolicy.requireValues(current.getResourceType(), request.getSortOrder());
        var context = new UpdateConsoleResourceContext(current, request.getParentResourceId(), request.getDisplayName(),
            resourcePolicy.route(request.getComponentKey()), request.getComponentKey(), request.getPermissionId(),
            request.getSortOrder(), clock.instant().truncatedTo(ChronoUnit.MICROS));
        validateCandidate(context);
        if (context.unchanged()) {
            // 沿用既有无变化保存的审计抑制；这里没有请求重放或幂等记录。
            audit.suppressReplay();
            return current.mutation();
        }
        var fact = ConsoleResourceAuditFacts.changed(request.getActor(), actor.member().tenantId(), current, context,
            current.getStatus(), current.getVersion() + 1, request.getReason(), context.getUpdateTime());
        resources.update(context);
        audit.record(() -> fact);
        return current.mutation();
    }

    /** 固定 Channel 后核对原版本，不把其他资源或旧版本当作本次操作。 */
    ConsoleResource requireResource(String code, int expectedVersion) {
        var row = repository.resource(code);
        if (row == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        row.requireVersion(expectedVersion);
        return row;
    }

    /** 编辑 Context 直接提供候选事实，不复制或提前修改原持久化对象。 */
    void validateCandidate(UpdateConsoleResourceContext context) {
        var tree = repository.resources(ConsoleResourceCapabilities.MAX_RESOURCES + 1);
        resourcePolicy.requireTreeSize(tree.size());
        resourcePolicy.requireEditable(context.getResource(), context, tree);
        var candidate = new ArrayList<ConsoleResourceDefinition>(tree);
        candidate.removeIf(row -> row.getResourceId() == context.getResourceId());
        candidate.add(context);
        resourcePolicy.validate(context.getResource(), context, candidate);
    }
}
