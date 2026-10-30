package com.paytech.platform.support.configuration;

import org.apache.ibatis.type.TypeHandlerRegistry;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler;
import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;
import com.paytech.platform.support.accesscontrol.domain.enums.*;
import com.paytech.platform.support.api.enums.*;
import com.paytech.platform.support.auditsecurity.domain.enums.OperationsAuditChainStatus;
import com.paytech.platform.support.auditsecurity.domain.enums.OperationsAuditClassification;

/**
 * Platform Support 持久化基础配置。
 */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = {"com.paytech.platform.support.accesscontrol.core.dal.mapper",
    "com.paytech.platform.support.filedocument.core.dal.mapper",
    "com.paytech.platform.support.delivery.core.dal.mapper",
    "com.paytech.platform.support.auditsecurity.core.dal.mapper",
    "com.paytech.platform.support.core.reliability.mapper",
    "com.paytech.platform.support.referencedata.core.dal.mapper",
    "com.paytech.platform.support.workflow.core.dal.mapper",
    "com.paytech.platform.support.dataquery.core.dal.mapper",
    "com.paytech.platform.support.localization.core.dal.mapper"}, sqlSessionTemplateRef = "sqlSessionTemplate")
public class PersistenceConfiguration {

    /**
     * 统一关闭数据库生成主键并注册 UTC Instant 转换器。
     *
     * @return MyBatis 配置定制器
     */
    @Bean
    ConfigurationCustomizer persistenceConfigurationCustomizer() {
        return configuration -> {
            configuration.setUseGeneratedKeys(false);
            configuration.getTypeHandlerRegistry().register(InstantTypeHandler.class);
            registerCodeEnumHandlers(configuration.getTypeHandlerRegistry());
        };
    }

    private void registerCodeEnumHandlers(TypeHandlerRegistry registry) {
        registry.register(com.paytech.platform.support.delivery.core.enums.NotificationMedium.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.delivery.core.enums.NotificationDeliveryStatus.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.delivery.core.enums.NotificationAttemptStatus.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.delivery.core.enums.NotificationResolutionSource.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.delivery.core.enums.NotificationEventOutcome.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.delivery.core.enums.WebhookEndpointStatus.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.delivery.core.enums.WebhookSecretStatus.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.delivery.core.enums.WebhookDeliveryStatus.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.delivery.core.enums.WebhookAttemptStatus.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.delivery.core.enums.WebhookFailureCode.class, CodeEnumTypeHandler.class);

        registry.register(com.paytech.platform.support.api.enums.FileScanStatus.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.api.enums.FileObjectStatus.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.filedocument.domain.enums.FileScanType.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.filedocument.domain.enums.FileUploadStatus.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.filedocument.core.enums.FileAccessLevel.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.filedocument.core.enums.FileVersionStatus.class, CodeEnumTypeHandler.class);
        registry.register(FileStorageType.class, CodeEnumTypeHandler.class);
        registry.register(FileStorageCredentialMode.class, CodeEnumTypeHandler.class);
        registry.register(FileStorageInstanceStatus.class, CodeEnumTypeHandler.class);
        registry.register(FileStorageBucketStatus.class, CodeEnumTypeHandler.class);
        registry.register(FileStorageCheckResult.class, CodeEnumTypeHandler.class);
        registry.register(AccessTenantType.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.accesscontrol.domain.enums.TenantMemberType.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.accesscontrol.domain.enums.TenantRoleScope.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.accesscontrol.domain.enums.TenantRoleStatus.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.accesscontrol.domain.enums.TenantGrantStatus.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.accesscontrol.domain.enums.TenantInvitationStatus.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.accesscontrol.domain.enums.RolePermissionStatus.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.accesscontrol.domain.enums.PermissionStatus.class, CodeEnumTypeHandler.class);

        registry.register(ApiCredentialStatus.class, CodeEnumTypeHandler.class);
        registry.register(GatewayRouteChannel.class, CodeEnumTypeHandler.class);
        registry.register(GatewayConfigurationStatus.class, CodeEnumTypeHandler.class);
        registry.register(GatewayHttpMethod.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.api.enums.CurrencyRoundingMode.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.api.enums.CurrencySpecStatus.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.api.enums.ReferenceDataStatus.class, CodeEnumTypeHandler.class);
        registry.register(GatewaySecurityProfileCode.class, CodeEnumTypeHandler.class);
        registry.register(GatewayAuthorizationMode.class, CodeEnumTypeHandler.class);
        registry.register(GatewayRequestCookieMode.class, CodeEnumTypeHandler.class);
        registry.register(GatewayResponseCookieMode.class, CodeEnumTypeHandler.class);
        registry.register(GatewayIdempotencyMode.class, CodeEnumTypeHandler.class);
        registry.register(GatewayRouteHeaderUse.class, CodeEnumTypeHandler.class);
        registry.register(GatewayGuardRuleType.class, CodeEnumTypeHandler.class);
        registry.register(GatewayGuardDimension.class, CodeEnumTypeHandler.class);
        registry.register(ClientSessionContextType.class, CodeEnumTypeHandler.class);
        registry.register(ClientChallengePurpose.class, CodeEnumTypeHandler.class);
        registry.register(ClientChallengeStatus.class, CodeEnumTypeHandler.class);
        registry.register(AuthenticationRealm.class, CodeEnumTypeHandler.class);
        registry.register(OperationsAccountType.class, CodeEnumTypeHandler.class);
        registry.register(OperationsAccountStatus.class, CodeEnumTypeHandler.class);
        registry.register(OperationsCredentialStatus.class, CodeEnumTypeHandler.class);
        registry.register(OperationsMemberStatus.class, CodeEnumTypeHandler.class);
        registry.register(OperationsContextType.class, CodeEnumTypeHandler.class);
        registry.register(OperationsSessionStatus.class, CodeEnumTypeHandler.class);
        registry.register(OperationsRefreshStatus.class, CodeEnumTypeHandler.class);
        registry.register(OperationsTenantOwnerStatus.class, CodeEnumTypeHandler.class);
        registry.register(OperationsTenantSecurityStatus.class, CodeEnumTypeHandler.class);
        registry.register(OperationsConsoleResourceType.class, CodeEnumTypeHandler.class);
        registry.register(OperationsAuditChainStatus.class, CodeEnumTypeHandler.class);
        registry.register(OperationsAuditClassification.class, CodeEnumTypeHandler.class);
        registry.register(OperationsPermissionRiskLevel.class, CodeEnumTypeHandler.class);
        registry.register(JwtSigningAlgorithm.class, CodeEnumTypeHandler.class);
        registry.register(JwtSigningKeyStatus.class, CodeEnumTypeHandler.class);

        registry.register(com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.dataquery.core.enums.DataSourceStatus.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.dataquery.core.enums.DataSourceCheckResult.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.dataquery.core.enums.QueryKeyValueType.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.dataquery.core.enums.QueryKeyRole.class, CodeEnumTypeHandler.class);
        registry.register(com.paytech.platform.support.dataquery.core.enums.QueryKeyTypeStatus.class, CodeEnumTypeHandler.class);
        registerWorkflowHandlers(registry);
        registerLocalizationHandlers(registry);
    }

    /** 多语言八张表的闭合枚举列。 */
    private void registerLocalizationHandlers(TypeHandlerRegistry registry) {
        for (Class<?> type : java.util.List.of(
            com.paytech.platform.support.localization.core.enums.TextDirection.class,
            com.paytech.platform.support.localization.core.enums.LocaleStatus.class,
            com.paytech.platform.support.localization.core.enums.ApplicationStatus.class,
            com.paytech.platform.support.localization.core.enums.KeyStatus.class,
            com.paytech.platform.support.localization.core.enums.ReleaseStatus.class,
            com.paytech.platform.support.localization.core.enums.ReleaseChangeType.class)) {
            registry.register(type, CodeEnumTypeHandler.class);
        }
    }

    /** 工作流十张表的闭合枚举列。 */
    private void registerWorkflowHandlers(TypeHandlerRegistry registry) {
        for (Class<?> type : java.util.List.of(
            com.paytech.platform.support.workflow.core.enums.WorkflowDefinitionStatus.class,
            com.paytech.platform.support.workflow.core.enums.WorkflowVersionStatus.class,
            com.paytech.platform.support.workflow.core.enums.WorkflowActionType.class,
            com.paytech.platform.support.workflow.core.enums.ActionTemplateStatus.class,
            com.paytech.platform.support.workflow.core.enums.WorkflowInstanceStatus.class,
            com.paytech.platform.support.workflow.core.enums.WorkflowNodeType.class,
            com.paytech.platform.support.workflow.core.enums.NodeRunStatus.class,
            com.paytech.platform.support.workflow.core.enums.ActionRunStatus.class,
            com.paytech.platform.support.workflow.core.enums.DataQueryCheckResult.class,
            com.paytech.platform.support.workflow.core.enums.VerificationResult.class,
            com.paytech.platform.support.workflow.core.enums.ApprovalStatus.class,
            com.paytech.platform.support.workflow.core.enums.TransferTargetType.class,
            com.paytech.platform.support.workflow.core.enums.NotificationTrigger.class,
            com.paytech.platform.support.workflow.core.enums.NotificationRuleType.class,
            com.paytech.platform.support.workflow.core.enums.CallbackStatus.class)) {
            registry.register(type, CodeEnumTypeHandler.class);
        }
    }

    /**
     * 提供本地多次读取所需的只读一致性事务。
     *
     * @param transactionManager
     *            JDBC 事务管理器
     * @return 显式事务模板
     */
    @Bean
    TransactionTemplate transactionTemplate(
        @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setTimeout(2);
        return transaction;
    }
}
