package com.paytech.operations.console.bff.ext.rest;

import java.time.Instant;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformFileStorageClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 存储管理页与文件记录页的浏览器入口，全部简单转发 Platform
 * <li>每个路由由 Gateway 绑定权限并经 PLT-I02 实时授权，Platform 再次校验：存储配置查询（实例、写入路由、检查策略）为
 * FILE_STORAGE_READ，其余存储配置写操作为 FILE_STORAGE_MANAGE；文件记录查询为 FILE_RECORD_READ，下载为 FILE_RECORD_DOWNLOAD。</li>
 * <li>Query 与 Body 按配置管理的严格边界解析：未知字段、重复或多余 Query、查询或检查带 Body 均在访问 Platform 前拒绝。</li>
 * <li>新建实例与更换密钥的 Body 含存储密钥：整个 /storage-admin 前缀只记元数据日志，BFF 不保存、不回显。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/storage-admin/v1")
public class FileStorageAdminController {
    private static final String INSTANCE_CODE = "[A-Z][A-Z0-9_]{0,63}";
    private static final String BUCKET_NAME = "[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]";
    private static final String SOURCE_SYSTEM = "\\*|[a-z][a-z0-9-]{0,63}";
    private static final String ACCESS_LEVEL = "PRIVATE|PUBLIC";
    private static final String POLICY_CODE = "[A-Z][A-Z0-9_]{0,63}";
    private static final String FILE_ID = "[1-9][0-9]{0,18}";
    private static final String SCAN_STATUS = "[A-Z][A-Z_]{0,31}";
    private static final String QUERY_SOURCE_SYSTEM = "[a-z][a-z0-9-]{0,63}";
    private static final String REASON_CODE = "[A-Z][A-Z0-9_]{0,63}";

    private final PlatformFileStorageClient client;

    /** 全部存储实例及其桶；页面据 version 提交修改与启停。 */
    @GetMapping("/instances")
    public StorageInstanceListResponseBody listInstances(OperatorContext context) {
        return client.listInstances(context);
    }

    /** 新建 DRAFT 存储实例；STATIC 凭据由 Platform 先存入 Vault。 */
    @PostMapping("/instances")
    public ConfigurationMutationResponseBody createInstance(OperatorContext context,
        @Valid @RequestBody CreateStorageInstanceRequestBody request) {
        return client.createInstance(context, request);
    }

    /** 修改实例地址、区域、路径风格、证书与超时。 */
    @PutMapping("/instances/{instanceCode}")
    public ConfigurationMutationResponseBody updateInstance(OperatorContext context,
        @PathVariable("instanceCode") @Pattern(regexp = INSTANCE_CODE) String instanceCode,
        @Valid @RequestBody UpdateStorageInstanceRequestBody request) {
        return client.updateInstance(context, instanceCode, request);
    }

    /** 更换密钥或切换凭据方式，保存后立即生效。 */
    @PutMapping("/instances/{instanceCode}/credential")
    public ConfigurationMutationResponseBody replaceCredential(OperatorContext context,
        @PathVariable("instanceCode") @Pattern(regexp = INSTANCE_CODE) String instanceCode,
        @Valid @RequestBody ReplaceStorageCredentialRequestBody request) {
        return client.replaceCredential(context, instanceCode, request);
    }

    /** 启用或停用实例；被写入路由引用时停用由 Platform 拒绝。 */
    @PatchMapping("/instances/{instanceCode}/status")
    public ConfigurationMutationResponseBody changeInstanceStatus(OperatorContext context,
        @PathVariable("instanceCode") @Pattern(regexp = INSTANCE_CODE) String instanceCode,
        @Valid @RequestBody ChangeStorageStatusRequestBody request) {
        return client.changeInstanceStatus(context, instanceCode, request);
    }

    /** 登记已由基础设施创建的桶，初始为 DRAFT。 */
    @PostMapping("/instances/{instanceCode}/buckets")
    public ConfigurationMutationResponseBody registerBucket(OperatorContext context,
        @PathVariable("instanceCode") @Pattern(regexp = INSTANCE_CODE) String instanceCode,
        @Valid @RequestBody RegisterStorageBucketRequestBody request) {
        return client.registerBucket(context, instanceCode, request);
    }

    /** 修改 DRAFT 公开桶的对外基础地址。 */
    @PutMapping("/instances/{instanceCode}/buckets/{bucketName}")
    public ConfigurationMutationResponseBody updateBucket(OperatorContext context,
        @PathVariable("instanceCode") @Pattern(regexp = INSTANCE_CODE) String instanceCode,
        @PathVariable("bucketName") @Pattern(regexp = BUCKET_NAME) String bucketName,
        @Valid @RequestBody UpdateStorageBucketRequestBody request) {
        return client.updateBucket(context, instanceCode, bucketName, request);
    }

    /** 检查桶的存在、版本控制、默认加密、读写与匿名访问；不接受 Body。 */
    @PostMapping("/instances/{instanceCode}/buckets/{bucketName}/check")
    public StorageBucketCheckResponseBody checkBucket(OperatorContext context,
        @PathVariable("instanceCode") @Pattern(regexp = INSTANCE_CODE) String instanceCode,
        @PathVariable("bucketName") @Pattern(regexp = BUCKET_NAME) String bucketName) {
        return client.checkBucket(context, instanceCode, bucketName);
    }

    /** 启用、只读或停用桶。 */
    @PatchMapping("/instances/{instanceCode}/buckets/{bucketName}/status")
    public ConfigurationMutationResponseBody changeBucketStatus(OperatorContext context,
        @PathVariable("instanceCode") @Pattern(regexp = INSTANCE_CODE) String instanceCode,
        @PathVariable("bucketName") @Pattern(regexp = BUCKET_NAME) String bucketName,
        @Valid @RequestBody ChangeStorageStatusRequestBody request) {
        return client.changeBucketStatus(context, instanceCode, bucketName, request);
    }

    /** 全部写入路由，含默认路由 *。 */
    @GetMapping("/routes")
    public StorageRouteListResponseBody listRoutes(OperatorContext context) {
        return client.listRoutes(context);
    }

    /** 新建写入路由。 */
    @PostMapping("/routes")
    public ConfigurationMutationResponseBody createRoute(OperatorContext context,
        @Valid @RequestBody CreateStorageRouteRequestBody request) {
        return client.createRoute(context, request);
    }

    /** 修改写入路由；只影响之后的新上传。 */
    @PutMapping("/routes/{sourceSystem}/{accessLevel}")
    public ConfigurationMutationResponseBody updateRoute(OperatorContext context,
        @PathVariable("sourceSystem") @Pattern(regexp = SOURCE_SYSTEM) String sourceSystem,
        @PathVariable("accessLevel") @Pattern(regexp = ACCESS_LEVEL) String accessLevel,
        @Valid @RequestBody UpdateStorageRouteRequestBody request) {
        return client.updateRoute(context, sourceSystem, accessLevel, request);
    }

    /** 删除写入路由；默认私有路由 (*, PRIVATE) 由 Platform 拒绝。 */
    @DeleteMapping("/routes/{sourceSystem}/{accessLevel}")
    public ConfigurationMutationResponseBody deleteRoute(OperatorContext context,
        @PathVariable("sourceSystem") @Pattern(regexp = SOURCE_SYSTEM) String sourceSystem,
        @PathVariable("accessLevel") @Pattern(regexp = ACCESS_LEVEL) String accessLevel,
        @Valid @RequestBody DeleteStorageRouteRequestBody request) {
        return client.deleteRoute(context, sourceSystem, accessLevel, request);
    }

    /** 检查策略列表与格式检查器能识别的类型。 */
    @GetMapping("/scan-policies")
    public ScanPolicyListResponseBody listScanPolicies(OperatorContext context) {
        return client.listScanPolicies(context);
    }

    /** 检查策略详情：引用的写入路由与全部修订。 */
    @GetMapping("/scan-policies/{policyCode}")
    public ScanPolicyDetailResponseBody getScanPolicy(OperatorContext context,
        @PathVariable("policyCode") @Pattern(regexp = POLICY_CODE) String policyCode) {
        return client.getScanPolicy(context, policyCode);
    }

    /** 新建检查策略及其草稿修订 1。 */
    @PostMapping("/scan-policies")
    public ConfigurationMutationResponseBody createScanPolicy(OperatorContext context,
        @Valid @RequestBody CreateScanPolicyRequestBody request) {
        return client.createScanPolicy(context, request);
    }

    /** 以生效修订为模板新建草稿修订。 */
    @PostMapping("/scan-policies/{policyCode}/revisions")
    public ConfigurationMutationResponseBody createScanPolicyRevision(OperatorContext context,
        @PathVariable("policyCode") @Pattern(regexp = POLICY_CODE) String policyCode,
        @Valid @RequestBody CreateScanPolicyRevisionRequestBody request) {
        return client.createScanPolicyRevision(context, policyCode, request);
    }

    /** 修改草稿修订。 */
    @PutMapping("/scan-policies/{policyCode}/revisions/{revisionNo}")
    public ConfigurationMutationResponseBody updateScanPolicyRevision(OperatorContext context,
        @PathVariable("policyCode") @Pattern(regexp = POLICY_CODE) String policyCode,
        @PathVariable("revisionNo") @Min(1) @Max(Integer.MAX_VALUE) int revisionNo,
        @Valid @RequestBody UpdateScanPolicyRevisionRequestBody request) {
        return client.updateScanPolicyRevision(context, policyCode, revisionNo, request);
    }

    /** 废弃草稿修订。 */
    @DeleteMapping("/scan-policies/{policyCode}/revisions/{revisionNo}")
    public ConfigurationMutationResponseBody discardScanPolicyRevision(OperatorContext context,
        @PathVariable("policyCode") @Pattern(regexp = POLICY_CODE) String policyCode,
        @PathVariable("revisionNo") @Min(1) @Max(Integer.MAX_VALUE) int revisionNo,
        @Valid @RequestBody DiscardScanPolicyRevisionRequestBody request) {
        return client.discardScanPolicyRevision(context, policyCode, revisionNo, request);
    }

    /** 激活草稿修订，原生效修订停用。 */
    @PostMapping("/scan-policies/{policyCode}/revisions/{revisionNo}/activation")
    public ConfigurationMutationResponseBody activateScanPolicyRevision(OperatorContext context,
        @PathVariable("policyCode") @Pattern(regexp = POLICY_CODE) String policyCode,
        @PathVariable("revisionNo") @Min(1) @Max(Integer.MAX_VALUE) int revisionNo,
        @Valid @RequestBody ActivateScanPolicyRevisionRequestBody request) {
        return client.activateScanPolicyRevision(context, policyCode, revisionNo, request);
    }

    /** 停用生效修订；被写入路由引用时由 Platform 拒绝。 */
    @PostMapping("/scan-policies/{policyCode}/retirement")
    public ConfigurationMutationResponseBody retireScanPolicy(OperatorContext context,
        @PathVariable("policyCode") @Pattern(regexp = POLICY_CODE) String policyCode,
        @Valid @RequestBody RetireScanPolicyRequestBody request) {
        return client.retireScanPolicy(context, policyCode, request);
    }

    /** 文件记录分页；时间范围必填与 31 天上限由 Platform 校验。 */
    @GetMapping("/files")
    public PagingResponseBody<FileRecordSummary> listFileRecords(OperatorContext context,
        @RequestParam(value = "createdFrom", required = false) Instant createdFrom,
        @RequestParam(value = "createdTo", required = false) Instant createdTo,
        @RequestParam(value = "fileId", required = false) @Pattern(regexp = FILE_ID) String fileId,
        @RequestParam(value = "sourceSystem", required = false) @Pattern(regexp = QUERY_SOURCE_SYSTEM) String sourceSystem,
        @RequestParam(value = "reasonCode", required = false) @Pattern(regexp = REASON_CODE) String reasonCode,
        @RequestParam(value = "accessLevel", required = false) @Pattern(regexp = ACCESS_LEVEL) String accessLevel,
        @RequestParam(value = "scanStatus", required = false) @Pattern(regexp = SCAN_STATUS) String scanStatus,
        @RequestParam(value = "hasPendingReplacement", required = false) Boolean hasPendingReplacement,
        @RequestParam(value = "page", required = false) @Min(1) @Max(100000) Integer page,
        @RequestParam(value = "size", required = false) @Min(1) @Max(200) Integer size) {
        return client.listFileRecords(context, new PlatformFileStorageClient.FileRecordQuery(createdFrom, createdTo, fileId,
            sourceSystem, reasonCode, accessLevel, scanStatus, hasPendingReplacement, page, size));
    }

    /** 文件记录详情：版本历史、检查结果与上传记录。 */
    @GetMapping("/files/{fileId}")
    public FileRecordDetailResponseBody getFileRecord(OperatorContext context,
        @PathVariable("fileId") @Pattern(regexp = FILE_ID) String fileId) {
        return client.getFileRecord(context, fileId);
    }

    /** 签发某一版本的短时下载地址并由 Platform 写下载审计；不接受 Body，地址不进入日志。 */
    @PostMapping("/files/{fileId}/download")
    public FileDownloadResponseBody downloadFileRecord(OperatorContext context,
        @PathVariable("fileId") @Pattern(regexp = FILE_ID) String fileId,
        @RequestParam(value = "versionNo", required = false) @Min(1) @Max(Integer.MAX_VALUE) Integer versionNo) {
        return client.downloadFileRecord(context, fileId, versionNo);
    }
}
