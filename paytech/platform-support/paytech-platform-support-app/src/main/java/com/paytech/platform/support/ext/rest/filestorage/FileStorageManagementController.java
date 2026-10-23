package com.paytech.platform.support.ext.rest.filestorage;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.filestorage.FileStorageManagementApi;
import com.paytech.platform.support.api.filestorage.request.ActivateScanPolicyRevisionRequestBody;
import com.paytech.platform.support.api.filestorage.request.ChangeStorageStatusRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateScanPolicyRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateScanPolicyRevisionRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateStorageInstanceRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.request.DeleteStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.request.DiscardScanPolicyRevisionRequestBody;
import com.paytech.platform.support.api.filestorage.request.RegisterStorageBucketRequestBody;
import com.paytech.platform.support.api.filestorage.request.ReplaceStorageCredentialRequestBody;
import com.paytech.platform.support.api.filestorage.request.RetireScanPolicyRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateScanPolicyRevisionRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateStorageBucketRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateStorageInstanceRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.response.FileRecordDetailResponseBody;
import com.paytech.platform.support.api.filestorage.response.FileRecordSummary;
import com.paytech.platform.support.api.filestorage.response.ScanPolicyDetailResponseBody;
import com.paytech.platform.support.api.filestorage.response.ScanPolicyListResponseBody;
import com.paytech.platform.support.api.filestorage.response.StorageBucketCheckResponseBody;
import com.paytech.platform.support.api.filestorage.response.StorageInstanceListResponseBody;
import com.paytech.platform.support.api.filestorage.response.StorageRouteListResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.service.processor.filestorage.ActivateScanPolicyRevisionProcessor;
import com.paytech.platform.support.service.processor.filestorage.ChangeStorageBucketStatusProcessor;
import com.paytech.platform.support.service.processor.filestorage.ChangeStorageInstanceStatusProcessor;
import com.paytech.platform.support.service.processor.filestorage.CheckStorageBucketManagementProcessor;
import com.paytech.platform.support.service.processor.filestorage.CreateScanPolicyProcessor;
import com.paytech.platform.support.service.processor.filestorage.CreateScanPolicyRevisionProcessor;
import com.paytech.platform.support.service.processor.filestorage.CreateStorageInstanceProcessor;
import com.paytech.platform.support.service.processor.filestorage.CreateStorageRouteProcessor;
import com.paytech.platform.support.service.processor.filestorage.DeleteStorageRouteProcessor;
import com.paytech.platform.support.service.processor.filestorage.DiscardScanPolicyRevisionProcessor;
import com.paytech.platform.support.service.processor.filestorage.DownloadFileRecordProcessor;
import com.paytech.platform.support.service.processor.filestorage.GetFileRecordProcessor;
import com.paytech.platform.support.service.processor.filestorage.GetScanPolicyProcessor;
import com.paytech.platform.support.service.processor.filestorage.ListFileRecordsProcessor;
import com.paytech.platform.support.service.processor.filestorage.ListScanPoliciesProcessor;
import com.paytech.platform.support.service.processor.filestorage.ListStorageInstancesProcessor;
import com.paytech.platform.support.service.processor.filestorage.ListStorageRoutesProcessor;
import com.paytech.platform.support.service.processor.filestorage.RegisterStorageBucketProcessor;
import com.paytech.platform.support.service.processor.filestorage.ReplaceStorageCredentialProcessor;
import com.paytech.platform.support.service.processor.filestorage.RetireScanPolicyProcessor;
import com.paytech.platform.support.service.processor.filestorage.UpdateScanPolicyRevisionProcessor;
import com.paytech.platform.support.service.processor.filestorage.UpdateStorageBucketProcessor;
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

    private static final String SCAN_POLICY = "FILE_SCAN_POLICY";

    private static final String SCAN_POLICY_CATALOG = "FILE_SCAN_POLICY_CATALOG";

    private static final String FILE = "FILE";

    private static final String FILE_CATALOG = "FILE_RECORD_CATALOG";

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
    private final UpdateStorageBucketProcessor updateBucket;
    private final FileScanPolicyConverter policyConverter;
    private final ListScanPoliciesProcessor listPolicies;
    private final GetScanPolicyProcessor getPolicy;
    private final CreateScanPolicyProcessor createPolicy;
    private final CreateScanPolicyRevisionProcessor createRevision;
    private final UpdateScanPolicyRevisionProcessor updateRevision;
    private final DiscardScanPolicyRevisionProcessor discardRevision;
    private final ActivateScanPolicyRevisionProcessor activateRevision;
    private final RetireScanPolicyProcessor retirePolicy;
    private final FileRecordConverter recordConverter;
    private final ListFileRecordsProcessor listRecords;
    private final GetFileRecordProcessor getRecord;
    private final DownloadFileRecordProcessor downloadRecord;

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

    /** 修改 DRAFT 公开桶的对外基础地址。 */
    @Audit(action = "OPERATIONS_FILE_STORAGE_BUCKET_UPDATE", resourceType = BUCKET, fallbackResourceId = CATALOG)
    @Override
    public ConfigurationMutationResponseBody updateBucket(String actor, String session, String instanceCode,
        String bucketName, UpdateStorageBucketRequestBody requestBody) {
        return converter.mutation(updateBucket.process(
            converter.updateBucket(actor, session, instanceCode, bucketName, requestBody)));
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
        String accessLevel, UpdateStorageRouteRequestBody requestBody) {
        return converter.mutation(
            updateRoute.process(converter.updateRoute(actor, session, sourceSystem, accessLevel, requestBody)));
    }

    /** 删除路由。 */
    @Audit(action = "OPERATIONS_FILE_STORAGE_ROUTE_DELETE", resourceType = ROUTE, fallbackResourceId = CATALOG)
    @Override
    public ConfigurationMutationResponseBody deleteRoute(String actor, String session, String sourceSystem,
        String accessLevel, DeleteStorageRouteRequestBody requestBody) {
        return converter.mutation(
            deleteRoute.process(converter.deleteRoute(actor, session, sourceSystem, accessLevel, requestBody)));
    }

    /** 检查策略列表。 */
    @Audit(action = "OPERATIONS_FILE_SCAN_POLICIES", resourceType = SCAN_POLICY, fallbackResourceId = SCAN_POLICY_CATALOG,
        recordSuccess = false)
    @Override
    public ScanPolicyListResponseBody listScanPolicies(String actor, String session) {
        return policyConverter.list(listPolicies.process(policyConverter.query(actor, session, null)));
    }

    /** 检查策略详情。 */
    @Audit(action = "OPERATIONS_FILE_SCAN_POLICIES", resourceType = SCAN_POLICY, resourceIdParameter = "policyCode",
        fallbackResourceId = SCAN_POLICY_CATALOG, recordSuccess = false)
    @Override
    public ScanPolicyDetailResponseBody getScanPolicy(String actor, String session, String policyCode) {
        return policyConverter.detail(getPolicy.process(policyConverter.query(actor, session, policyCode)));
    }

    /** 新建检查策略。 */
    @Audit(action = "OPERATIONS_FILE_SCAN_POLICY_CREATE", resourceType = SCAN_POLICY,
        fallbackResourceId = SCAN_POLICY_CATALOG)
    @Override
    public ConfigurationMutationResponseBody createScanPolicy(String actor, String session,
        CreateScanPolicyRequestBody requestBody) {
        return converter.mutation(createPolicy.process(policyConverter.create(actor, session, requestBody)));
    }

    /** 以 ACTIVE 为模板新建修订。 */
    @Audit(action = "OPERATIONS_FILE_SCAN_POLICY_REVISION_CREATE", resourceType = SCAN_POLICY,
        resourceIdParameter = "policyCode", fallbackResourceId = SCAN_POLICY_CATALOG)
    @Override
    public ConfigurationMutationResponseBody createScanPolicyRevision(String actor, String session, String policyCode,
        CreateScanPolicyRevisionRequestBody requestBody) {
        return converter.mutation(
            createRevision.process(policyConverter.createRevision(actor, session, policyCode, requestBody)));
    }

    /** 修改草稿修订。 */
    @Audit(action = "OPERATIONS_FILE_SCAN_POLICY_REVISION_UPDATE", resourceType = SCAN_POLICY,
        resourceIdParameter = "policyCode", fallbackResourceId = SCAN_POLICY_CATALOG)
    @Override
    public ConfigurationMutationResponseBody updateScanPolicyRevision(String actor, String session, String policyCode,
        int revisionNo, UpdateScanPolicyRevisionRequestBody requestBody) {
        return converter.mutation(updateRevision.process(
            policyConverter.updateRevision(actor, session, policyCode, revisionNo, requestBody)));
    }

    /** 废弃草稿修订。 */
    @Audit(action = "OPERATIONS_FILE_SCAN_POLICY_REVISION_DISCARD", resourceType = SCAN_POLICY,
        resourceIdParameter = "policyCode", fallbackResourceId = SCAN_POLICY_CATALOG)
    @Override
    public ConfigurationMutationResponseBody discardScanPolicyRevision(String actor, String session, String policyCode,
        int revisionNo, DiscardScanPolicyRevisionRequestBody requestBody) {
        return converter.mutation(discardRevision.process(
            policyConverter.discard(actor, session, policyCode, revisionNo, requestBody)));
    }

    /** 激活草稿修订。 */
    @Audit(action = "OPERATIONS_FILE_SCAN_POLICY_ACTIVATE", resourceType = SCAN_POLICY,
        resourceIdParameter = "policyCode", fallbackResourceId = SCAN_POLICY_CATALOG)
    @Override
    public ConfigurationMutationResponseBody activateScanPolicyRevision(String actor, String session,
        String policyCode, int revisionNo, ActivateScanPolicyRevisionRequestBody requestBody) {
        return converter.mutation(activateRevision.process(
            policyConverter.activate(actor, session, policyCode, revisionNo, requestBody)));
    }

    /** 停用检查策略。 */
    @Audit(action = "OPERATIONS_FILE_SCAN_POLICY_RETIRE", resourceType = SCAN_POLICY,
        resourceIdParameter = "policyCode", fallbackResourceId = SCAN_POLICY_CATALOG)
    @Override
    public ConfigurationMutationResponseBody retireScanPolicy(String actor, String session, String policyCode,
        RetireScanPolicyRequestBody requestBody) {
        return converter.mutation(retirePolicy.process(policyConverter.retire(actor, session, policyCode, requestBody)));
    }

    /** 文件记录列表。 */
    @Audit(action = "OPERATIONS_FILE_RECORDS", resourceType = FILE, fallbackResourceId = FILE_CATALOG,
        recordSuccess = false)
    @Override
    public PagingResponseBody<FileRecordSummary> listFileRecords(String actor, String session, Instant createdFrom,
        Instant createdTo, String fileId, String sourceSystem, String reasonCode, String accessLevel, String scanStatus,
        Boolean hasPendingReplacement, Integer page, Integer size) {
        return recordConverter.page(listRecords.process(recordConverter.list(actor, session, createdFrom, createdTo,
            fileId, sourceSystem, reasonCode, accessLevel, scanStatus, hasPendingReplacement, page, size)));
    }

    /** 文件记录详情。 */
    @Audit(action = "OPERATIONS_FILE_RECORDS", resourceType = FILE, resourceIdParameter = "fileId",
        fallbackResourceId = FILE_CATALOG, recordSuccess = false)
    @Override
    public FileRecordDetailResponseBody getFileRecord(String actor, String session, String fileId) {
        return recordConverter.detail(getRecord.process(recordConverter.detail(actor, session, fileId)));
    }

    /** 下载文件记录的某一版本；下载审计 FILE_DOWNLOAD_AUTHORIZE 由下载入口在签发成功后写入。 */
    @Override
    public FileDownloadResponseBody downloadFileRecord(String actor, String session, String fileId,
        Integer versionNo) {
        return recordConverter.download(downloadRecord.process(recordConverter.download(actor, session, fileId,
            versionNo)));
    }
}
