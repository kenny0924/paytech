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

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.ConsoleResourceManagementProperties;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ConsoleResource;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.*;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ConfigurationCatalogRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ConsoleResourceRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.ConsoleResourceManagementService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.CreateConsoleResourceRequest;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 整棵 Operations 树协调写入，保留数据库 SERIALIZABLE、唯一约束及版本保护。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateConsoleResourceProcessor
    extends AbstractLockProcessor<CreateConsoleResourceRequest, ConfigurationMutation> {
    private final Clock clock;
    private final ConsoleResourceRepository repository;
    private final ConfigurationCatalogRepository catalog;
    private final ConsoleResourceManagementService resources;
    private final OperationsManagementPolicyService policy;
    private final ConsoleResourcePolicy resourcePolicy;
    private final OperationsAuditService audit;
    private final PlatformTransactionManager transactionManager;
    private final ConsoleResourceManagementProperties properties;
    private final IdGenerator ids;

    /** 三个写入口共用树资源，模板在事务外获取并在事务完成后释放。 */
    @Override
    protected LockCommand lockCommand(CreateConsoleResourceRequest request) {
        return LockCommand.tryLock("console-resource:operations",
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** SERIALIZABLE 保护父子范围及管理保护闭包，禁止外部调用进入事务。 */
    @Override
    protected ConfigurationMutation processWithinLock(CreateConsoleResourceRequest request) {
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
    ConfigurationMutation execute(CreateConsoleResourceRequest request) {
        audit.observeTransaction();
        var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(), null, null,
            false, "CONSOLE_RESOURCE_MANAGE");
        if (repository.resource(request.getResourceCode()) != null) {
            throw new BizCheckFailException("资源编码已经存在");
        }
        resourcePolicy.requireBinding(catalog.lockPermission(request.getPermissionId(), false), null);
        resourcePolicy.requireValues(request.getResourceType(), request.getSortOrder());
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var row = new ConsoleResource(ids.nextId(), request.getResourceCode(), GatewayRouteChannel.OPERATIONS,
            request.getResourceType(), request.getParentResourceId(), request.getDisplayName(),
            resourcePolicy.route(request.getComponentKey()), request.getComponentKey(), request.getPermissionId(),
            request.getSortOrder(), GatewayConfigurationStatus.DISABLED, 1, now, now);
        var candidate = new ArrayList<>(repository.resources(ConsoleResourceCapabilities.MAX_RESOURCES + 1));
        resourcePolicy.requireTreeSize(candidate.size());
        candidate.add(row);
        resourcePolicy.validate(null, row, candidate);
        var context = new CreateConsoleResourceContext(row);
        resources.create(context);
        var fact = ConsoleResourceAuditFacts.changed(request.getActor(), actor.member().tenantId(), null, row,
            row.getStatus(), row.getVersion(), request.getReason(), now);
        audit.record(() -> fact);
        return row.mutation();
    }
}
