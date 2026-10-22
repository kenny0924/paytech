package com.paytech.operations.console.bff.ext.rest;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.operations.console.bff.integration.PlatformFileStorageClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 存储管理页的浏览器入口，全部简单转发 Platform
 * <li>每个路由由 Gateway 绑定 FILE_STORAGE_READ（两个列表）或 FILE_STORAGE_MANAGE（其余写操作），并经 PLT-I02 实时授权；Platform 再次校验。</li>
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

    /** 检查桶的存在、版本控制、默认加密与读写；不接受 Body。 */
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
    @PutMapping("/routes/{sourceSystem}")
    public ConfigurationMutationResponseBody updateRoute(OperatorContext context,
        @PathVariable("sourceSystem") @Pattern(regexp = SOURCE_SYSTEM) String sourceSystem,
        @Valid @RequestBody UpdateStorageRouteRequestBody request) {
        return client.updateRoute(context, sourceSystem, request);
    }

    /** 删除写入路由；默认路由由 Platform 拒绝。 */
    @DeleteMapping("/routes/{sourceSystem}")
    public ConfigurationMutationResponseBody deleteRoute(OperatorContext context,
        @PathVariable("sourceSystem") @Pattern(regexp = SOURCE_SYSTEM) String sourceSystem,
        @Valid @RequestBody DeleteStorageRouteRequestBody request) {
        return client.deleteRoute(context, sourceSystem, request);
    }
}
