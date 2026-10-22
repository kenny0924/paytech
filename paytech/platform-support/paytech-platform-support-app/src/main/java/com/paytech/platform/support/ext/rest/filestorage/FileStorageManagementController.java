package com.paytech.platform.support.ext.rest.filestorage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.filestorage.FileStorageManagementApi;
import com.paytech.platform.support.api.filestorage.request.ChangeStorageStatusRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateStorageInstanceRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.request.DeleteStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.request.RegisterStorageBucketRequestBody;
import com.paytech.platform.support.api.filestorage.request.ReplaceStorageCredentialRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateStorageInstanceRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.response.StorageBucketCheckResponseBody;
import com.paytech.platform.support.api.filestorage.response.StorageInstanceListResponseBody;
import com.paytech.platform.support.api.filestorage.response.StorageRouteListResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.service.processor.filestorage.ChangeStorageBucketStatusProcessor;
import com.paytech.platform.support.service.processor.filestorage.ChangeStorageInstanceStatusProcessor;
import com.paytech.platform.support.service.processor.filestorage.CheckStorageBucketManagementProcessor;
import com.paytech.platform.support.service.processor.filestorage.CreateStorageInstanceProcessor;
import com.paytech.platform.support.service.processor.filestorage.CreateStorageRouteProcessor;
import com.paytech.platform.support.service.processor.filestorage.DeleteStorageRouteProcessor;
import com.paytech.platform.support.service.processor.filestorage.ListStorageInstancesProcessor;
import com.paytech.platform.support.service.processor.filestorage.ListStorageRoutesProcessor;
import com.paytech.platform.support.service.processor.filestorage.RegisterStorageBucketProcessor;
import com.paytech.platform.support.service.processor.filestorage.ReplaceStorageCredentialProcessor;
import com.paytech.platform.support.service.processor.filestorage.UpdateStorageInstanceProcessor;
import com.paytech.platform.support.service.processor.filestorage.UpdateStorageRouteProcessor;

import lombok.AllArgsConstructor;

/** 存储管理的协议适配器；鉴权、规则、事务与审计事实委托 core 的 Processor。整个前缀只记录元数据日志。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class FileStorageManagementController implements FileStorageManagementApi {

    private static final String INSTANCE = "FILE_STORAGE_INSTANCE";

    private static final String BUCKET = "FILE_STORAGE_BUCKET";

    private static final String ROUTE = "FILE_STORAGE_ROUTE";

    private static final String CATALOG = "FILE_STORAGE_CATALOG";

    private final FileStorageManagementConverter converter;
    private final ListStorageRoutesProcessor listRoutes;
    private final CreateStorageRouteProcessor createRoute;
    private final UpdateStorageRouteProcessor updateRoute;
    private final DeleteStorageRouteProcessor deleteRoute;
    private final ListStorageInstancesProcessor listInstances;
    private final CreateStorageInstanceProcessor createInstance;
    private final UpdateStorageInstanceProcessor updateInstance;
    private final RegisterStorageBucketProcessor registerBucket;
    private final ReplaceStorageCredentialProcessor replaceCredential;
    private final ChangeStorageBucketStatusProcessor changeBucketStatus;
    private final ChangeStorageInstanceStatusProcessor changeInstanceStatus;
    private final CheckStorageBucketManagementProcessor checkBucket;

    /** 实例列表。 */
    @Audit(action = "OPERATIONS_FILE_STORAGE_INSTANCES", resourceType = INSTANCE, fallbackResourceId = CATALOG,
        recordSuccess = false)
    @Override
    public StorageInstanceListResponseBody listInstances(String actor, String session) {
        return converter.instances(listInstances.process(converter.list(actor, session)));
    }

    /** 新建实例。审计沿用真实业务结果。 */
    @Audit(action = "OPERATIONS_FILE_STORAGE_INSTANCE_CREATE", resourceType = INSTANCE, fallbackResourceId = CATALOG)
    @Override
    public ConfigurationMutationResponseBody createInstance(String actor, String session,
        CreateStorageInstanceRequestBody requestBody) {
        return converter.mutation(createInstance.process(converter.create(actor, session, requestBody)));
    }

    /** 修改实例连接设置。 */
    @Audit(action = "OPERATIONS_FILE_STORAGE_INSTANCE_UPDATE", resourceType = INSTANCE,
        fallbackResourceId = CATALOG)
    @Override
    public ConfigurationMutationResponseBody updateInstance(String actor, String session, String instanceCode,
        UpdateStorageInstanceRequestBody requestBody) {
        return converter.mutation(updateInstance.process(converter.update(actor, session, instanceCode, requestBody)));
    }

    /** 更换密钥或凭据方式。 */
    @Audit(action = "OPERATIONS_FILE_STORAGE_CREDENTIAL_REPLACE", resourceType = INSTANCE,
        fallbackResourceId = CATALOG)
    @Override
    public ConfigurationMutationResponseBody replaceCredential(String actor, String session, String instanceCode,
        ReplaceStorageCredentialRequestBody requestBody) {
        return converter.mutation(
            replaceCredential.process(converter.credential(actor, session, instanceCode, requestBody)));
    }

    /** 切换实例状态。 */
    @Audit(action = "OPERATIONS_FILE_STORAGE_INSTANCE_STATUS", resourceType = INSTANCE,
        fallbackResourceId = CATALOG)
    @Override
    public ConfigurationMutationResponseBody changeInstanceStatus(String actor, String session, String instanceCode,
        ChangeStorageStatusRequestBody requestBody) {
        return converter.mutation(
            changeInstanceStatus.process(converter.instanceStatus(actor, session, instanceCode, requestBody)));
    }

    /** 登记存储桶。 */
    @Audit(action = "OPERATIONS_FILE_STORAGE_BUCKET_REGISTER", resourceType = BUCKET,
        fallbackResourceId = CATALOG)
    @Override
    public ConfigurationMutationResponseBody registerBucket(String actor, String session, String instanceCode,
        RegisterStorageBucketRequestBody requestBody) {
        return converter.mutation(registerBucket.process(converter.bucket(actor, session, instanceCode, requestBody)));
    }

    /** 检查存储桶并回写结果。 */
    @Audit(action = "OPERATIONS_FILE_STORAGE_BUCKET_CHECK", resourceType = BUCKET, fallbackResourceId = CATALOG)
    @Override
    public StorageBucketCheckResponseBody checkBucket(String actor, String session, String instanceCode,
        String bucketName) {
        return converter.check(instanceCode, bucketName,
            checkBucket.process(converter.check(actor, session, instanceCode, bucketName)));
    }

    /** 切换存储桶状态。 */
    @Audit(action = "OPERATIONS_FILE_STORAGE_BUCKET_STATUS", resourceType = BUCKET, fallbackResourceId = CATALOG)
    @Override
    public ConfigurationMutationResponseBody changeBucketStatus(String actor, String session, String instanceCode,
        String bucketName, ChangeStorageStatusRequestBody requestBody) {
        return converter.mutation(changeBucketStatus.process(
            converter.bucketStatus(actor, session, instanceCode, bucketName, requestBody)));
    }

    /** 路由列表。 */
    @Audit(action = "OPERATIONS_FILE_STORAGE_ROUTES", resourceType = ROUTE, fallbackResourceId = CATALOG,
        recordSuccess = false)
    @Override
    public StorageRouteListResponseBody listRoutes(String actor, String session) {
        return converter.routes(listRoutes.process(converter.list(actor, session)));
    }

    /** 新建路由。 */
    @Audit(action = "OPERATIONS_FILE_STORAGE_ROUTE_CREATE", resourceType = ROUTE, fallbackResourceId = CATALOG)
    @Override
    public ConfigurationMutationResponseBody createRoute(String actor, String session,
        CreateStorageRouteRequestBody requestBody) {
        return converter.mutation(createRoute.process(converter.createRoute(actor, session, requestBody)));
    }

    /** 修改路由。 */
    @Audit(action = "OPERATIONS_FILE_STORAGE_ROUTE_UPDATE", resourceType = ROUTE, fallbackResourceId = CATALOG)
    @Override
    public ConfigurationMutationResponseBody updateRoute(String actor, String session, String sourceSystem,
        UpdateStorageRouteRequestBody requestBody) {
        return converter.mutation(
            updateRoute.process(converter.updateRoute(actor, session, sourceSystem, requestBody)));
    }

    /** 删除路由。 */
    @Audit(action = "OPERATIONS_FILE_STORAGE_ROUTE_DELETE", resourceType = ROUTE, fallbackResourceId = CATALOG)
    @Override
    public ConfigurationMutationResponseBody deleteRoute(String actor, String session, String sourceSystem,
        DeleteStorageRouteRequestBody requestBody) {
        return converter.mutation(
            deleteRoute.process(converter.deleteRoute(actor, session, sourceSystem, requestBody)));
    }
}
