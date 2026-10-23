package com.paytech.platform.support.ext.rest.filestorage;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.enums.FileStorageCredentialMode;
import com.paytech.platform.support.api.enums.FileStorageInstanceStatus;
import com.paytech.platform.support.api.enums.FileStorageType;
import com.paytech.platform.support.api.filestorage.request.ChangeStorageStatusRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateStorageInstanceRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.request.DeleteStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.request.RegisterStorageBucketRequestBody;
import com.paytech.platform.support.api.filestorage.request.ReplaceStorageCredentialRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateStorageBucketRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateStorageInstanceRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateStorageRouteRequestBody;
import com.paytech.platform.support.api.filestorage.response.StorageBucket;
import com.paytech.platform.support.api.filestorage.response.StorageBucketCheckResponseBody;
import com.paytech.platform.support.api.filestorage.response.StorageInstanceListResponseBody;
import com.paytech.platform.support.api.filestorage.response.StorageInstanceResponseBody;
import com.paytech.platform.support.api.filestorage.response.StorageRouteListResponseBody;
import com.paytech.platform.support.api.filestorage.response.StorageRouteResponseBody;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageInstanceView;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageRouteView;
import com.paytech.platform.support.filedocument.domain.storage.StorageInstanceSettings;
import com.paytech.platform.support.service.processor.filestorage.request.ChangeStorageBucketStatusRequest;
import com.paytech.platform.support.service.processor.filestorage.request.ChangeStorageInstanceStatusRequest;
import com.paytech.platform.support.service.processor.filestorage.request.CheckStorageBucketManagementRequest;
import com.paytech.platform.support.service.processor.filestorage.request.CreateStorageInstanceRequest;
import com.paytech.platform.support.service.processor.filestorage.request.DeleteStorageRouteRequest;
import com.paytech.platform.support.service.processor.filestorage.request.ListStorageRequest;
import com.paytech.platform.support.service.processor.filestorage.request.RegisterStorageBucketRequest;
import com.paytech.platform.support.service.processor.filestorage.request.ReplaceStorageCredentialRequest;
import com.paytech.platform.support.service.processor.filestorage.request.SaveStorageRouteRequest;
import com.paytech.platform.support.service.processor.filestorage.request.UpdateStorageBucketRequest;
import com.paytech.platform.support.service.processor.filestorage.request.UpdateStorageInstanceRequest;
import com.paytech.platform.support.service.processor.filestorage.response.StorageBucketCheckOutcome;

/**
 * 存储管理的协议转换：Code 按稳定编码解析，超时缺省取 2000 / 10000 毫秒，文本去除首尾空白
 * <li>不认识的状态与类型转换为空，由内部请求注解拒绝；密钥原样传入内存，不进入日志。</li>
 */
@Component
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class FileStorageManagementConverter {

    /** 默认连接超时毫秒数。 */
    static final int DEFAULT_CONNECT_TIMEOUT_MS = 2000;

    /** 默认读写超时毫秒数。 */
    static final int DEFAULT_READ_TIMEOUT_MS = 10000;

    /** 列表查询。 */
    ListStorageRequest list(String actor, String session) {
        return new ListStorageRequest(actor, session);
    }

    /** 新建实例：类型、凭据方式按 Code 解析。 */
    CreateStorageInstanceRequest create(String actor, String session, CreateStorageInstanceRequestBody body) {
        FileStorageType type = EnumUtils.getEnumByCode(FileStorageType.class, body.getStorageType());
        return new CreateStorageInstanceRequest(actor, session, body.getInstanceCode(),
            settings(type, body.getEndpoint(), body.getRegion(), body.getPathStyleAccess(),
                body.getTrustedCertificatePem(), body.getConnectTimeoutMs(), body.getReadTimeoutMs()),
            EnumUtils.getEnumByCode(FileStorageCredentialMode.class, body.getCredentialMode()), body.getAccessKey(),
            body.getSecretKey(), text(body.getReason()));
    }

    /** 修改实例连接设置；类型沿用原值。 */
    UpdateStorageInstanceRequest update(String actor, String session, String instanceCode,
        UpdateStorageInstanceRequestBody body) {
        return new UpdateStorageInstanceRequest(actor, session, instanceCode,
            settings(null, body.getEndpoint(), body.getRegion(), body.getPathStyleAccess(),
                body.getTrustedCertificatePem(), body.getConnectTimeoutMs(), body.getReadTimeoutMs()),
            body.getExpectedVersion(), text(body.getReason()));
    }

    /** 更换密钥或凭据方式。 */
    ReplaceStorageCredentialRequest credential(String actor, String session, String instanceCode,
        ReplaceStorageCredentialRequestBody body) {
        return new ReplaceStorageCredentialRequest(actor, session, instanceCode,
            EnumUtils.getEnumByCode(FileStorageCredentialMode.class, body.getCredentialMode()), body.getAccessKey(),
            body.getSecretKey(), body.getExpectedVersion(), text(body.getReason()));
    }

    /** 实例状态。 */
    ChangeStorageInstanceStatusRequest instanceStatus(String actor, String session, String instanceCode,
        ChangeStorageStatusRequestBody body) {
        return new ChangeStorageInstanceStatusRequest(actor, session, instanceCode, body.getExpectedVersion(),
            text(body.getReason()), EnumUtils.getEnumByCode(FileStorageInstanceStatus.class, body.getStatus()));
    }

    /** 登记存储桶。 */
    RegisterStorageBucketRequest bucket(String actor, String session, String instanceCode,
        RegisterStorageBucketRequestBody body) {
        return new RegisterStorageBucketRequest(actor, session, instanceCode, body.getBucketName(),
            accessLevel(body.getAccessLevel()), blankToNull(body.getPublicBaseUrl()), text(body.getReason()));
    }

    /** 修改 DRAFT 公开桶的对外基础地址。 */
    UpdateStorageBucketRequest updateBucket(String actor, String session, String instanceCode, String bucketName,
        UpdateStorageBucketRequestBody body) {
        return new UpdateStorageBucketRequest(actor, session, instanceCode, bucketName,
            blankToNull(body.getPublicBaseUrl()), body.getExpectedVersion(), text(body.getReason()));
    }

    /** 检查存储桶。 */
    CheckStorageBucketManagementRequest check(String actor, String session, String instanceCode, String bucketName) {
        return new CheckStorageBucketManagementRequest(actor, session, instanceCode, bucketName);
    }

    /** 存储桶状态。 */
    ChangeStorageBucketStatusRequest bucketStatus(String actor, String session, String instanceCode,
        String bucketName, ChangeStorageStatusRequestBody body) {
        return new ChangeStorageBucketStatusRequest(actor, session, instanceCode, bucketName,
            body.getExpectedVersion(), text(body.getReason()),
            EnumUtils.getEnumByCode(FileStorageBucketStatus.class, body.getStatus()));
    }

    /** 新建路由。 */
    SaveStorageRouteRequest createRoute(String actor, String session, CreateStorageRouteRequestBody body) {
        return new SaveStorageRouteRequest(actor, session, body.getSourceSystem(), accessLevel(body.getAccessLevel()),
            body.getInstanceCode(), body.getBucketName(), text(body.getReason()), body.getScanPolicyCode(),
            body.getMaxUploadSeconds(), 0);
    }

    /** 修改路由；来源系统与访问级别取 Path。 */
    SaveStorageRouteRequest updateRoute(String actor, String session, String sourceSystem, String accessLevel,
        UpdateStorageRouteRequestBody body) {
        return new SaveStorageRouteRequest(actor, session, sourceSystem, accessLevel(accessLevel),
            body.getInstanceCode(), body.getBucketName(), text(body.getReason()), body.getScanPolicyCode(),
            body.getMaxUploadSeconds(), body.getExpectedVersion());
    }

    /** 删除路由；来源系统与访问级别取 Path。 */
    DeleteStorageRouteRequest deleteRoute(String actor, String session, String sourceSystem, String accessLevel,
        DeleteStorageRouteRequestBody body) {
        return new DeleteStorageRouteRequest(actor, session, sourceSystem, accessLevel(accessLevel),
            body.getExpectedVersion(), text(body.getReason()));
    }

    /** 访问级别 Code；不认识的值转换为空，由内部请求注解拒绝。 */
    static FileAccessLevel accessLevel(String code) {
        return EnumUtils.getEnumByCode(FileAccessLevel.class, code);
    }

    /** 保存结果：主键输出十进制字符串，状态输出稳定 Code（路由为空）。 */
    ConfigurationMutationResponseBody mutation(FileStorageChange change) {
        return new ConfigurationMutationResponseBody(change.getObjectType(), Long.toString(change.getObjectId()),
            change.getObjectCode(), change.getStatus(), change.getAfterVersion());
    }

    /** 检查结果。 */
    StorageBucketCheckResponseBody check(String instanceCode, String bucketName, StorageBucketCheckOutcome outcome) {
        return new StorageBucketCheckResponseBody(instanceCode, bucketName, outcome.getResult().getCode(),
            outcome.getCheckedAt(), outcome.getChange().getAfterVersion());
    }

    /** 实例列表。 */
    StorageInstanceListResponseBody instances(List<FileStorageInstanceView> views) {
        return new StorageInstanceListResponseBody(views.stream().map(this::instance).toList());
    }

    /** 路由列表。 */
    StorageRouteListResponseBody routes(List<FileStorageRouteView> views) {
        return new StorageRouteListResponseBody(views.stream()
            .map(view -> new StorageRouteResponseBody(view.getSourceSystem(), view.getAccessLevel(),
                view.getInstanceCode(),
                view.getBucketName(), view.getScanPolicyCode(), view.getMaxUploadSeconds(), view.getVersion(),
                view.getUpdateTime()))
            .toList());
    }

    /** 实例与其存储桶。 */
    StorageInstanceResponseBody instance(FileStorageInstanceView view) {
        List<StorageBucket> buckets = view.getBuckets().stream()
            .map(bucket -> new StorageBucket(bucket.getBucketName(), bucket.getAccessLevel(), bucket.getPublicBaseUrl(),
                bucket.getStatus(), bucket.getLastCheckTime(),
                bucket.getLastCheckResult(), bucket.getRouteCount(), bucket.getVersion(), bucket.getUpdateTime()))
            .toList();
        return new StorageInstanceResponseBody(view.getInstanceCode(), view.getStorageType(), view.getEndpoint(),
            view.getRegion(), view.isPathStyleAccess(), view.getTrustedCertificatePem(), view.getCredentialMode(),
            view.isCredentialConfigured(), view.getCredentialUpdateTime(), view.getConnectTimeoutMs(),
            view.getReadTimeoutMs(), view.getStatus(), view.getVersion(), view.getUpdateTime(), view.getRouteCount(),
            buckets);
    }

    /** 连接设置：空文本视为未填写，超时缺省取默认值。 */
    StorageInstanceSettings settings(FileStorageType type, String endpoint, String region, Boolean pathStyle,
        String certificate, Integer connectTimeoutMs, Integer readTimeoutMs) {
        return new StorageInstanceSettings(type, blankToNull(endpoint), region, Boolean.TRUE.equals(pathStyle),
            blankToNull(certificate), connectTimeoutMs == null ? DEFAULT_CONNECT_TIMEOUT_MS : connectTimeoutMs,
            readTimeoutMs == null ? DEFAULT_READ_TIMEOUT_MS : readTimeoutMs);
    }

    /** 去除首尾空白；空值由内部请求注解拒绝。 */
    String text(String value) {
        return value == null ? null : value.strip();
    }

    /** 空白文本视为未填写。 */
    String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
