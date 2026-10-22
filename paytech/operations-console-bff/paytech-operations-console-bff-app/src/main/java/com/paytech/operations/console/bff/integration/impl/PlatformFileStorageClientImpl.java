package com.paytech.operations.console.bff.integration.impl;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.operations.console.bff.integration.PlatformFileStorageClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
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

import lombok.AllArgsConstructor;

/**
 * Platform 存储管理的 HTTP 转发：每个入口只调用一次 Platform，不重试；请求幂等由 Gateway 负责
 * <li>会话 Account ID 与 Session ID 填入 X-Gateway-Actor-Subject / X-Gateway-Session-Id。</li>
 * <li>Platform 422 的字段路径只投影到本次页面请求体内存在的路径，不传播远端文案；密钥字段值不进入错误响应。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class PlatformFileStorageClientImpl implements PlatformFileStorageClient {
    private final FileStorageManagementApi api;

    /** 实例列表原样返回。 */
    @Override
    public StorageInstanceListResponseBody listInstances(OperatorContext context) {
        return api.listInstances(context.accountId(), context.sessionId());
    }

    /** 新建实例；密钥只随本次请求转发。 */
    @Override
    public ConfigurationMutationResponseBody createInstance(OperatorContext context,
        CreateStorageInstanceRequestBody request) {
        return write(request, () -> api.createInstance(context.accountId(), context.sessionId(), request));
    }

    /** 修改实例连接设置。 */
    @Override
    public ConfigurationMutationResponseBody updateInstance(OperatorContext context, String instanceCode,
        UpdateStorageInstanceRequestBody request) {
        return write(request, () -> api.updateInstance(context.accountId(), context.sessionId(), instanceCode, request));
    }

    /** 更换密钥；密钥只随本次请求转发。 */
    @Override
    public ConfigurationMutationResponseBody replaceCredential(OperatorContext context, String instanceCode,
        ReplaceStorageCredentialRequestBody request) {
        return write(request, () -> api.replaceCredential(context.accountId(), context.sessionId(), instanceCode, request));
    }

    /** 切换实例状态。 */
    @Override
    public ConfigurationMutationResponseBody changeInstanceStatus(OperatorContext context, String instanceCode,
        ChangeStorageStatusRequestBody request) {
        return write(request, () -> api.changeInstanceStatus(context.accountId(), context.sessionId(), instanceCode, request));
    }

    /** 登记桶。 */
    @Override
    public ConfigurationMutationResponseBody registerBucket(OperatorContext context, String instanceCode,
        RegisterStorageBucketRequestBody request) {
        return write(request, () -> api.registerBucket(context.accountId(), context.sessionId(), instanceCode, request));
    }

    /** 检查桶；没有请求体。 */
    @Override
    public StorageBucketCheckResponseBody checkBucket(OperatorContext context, String instanceCode, String bucketName) {
        return api.checkBucket(context.accountId(), context.sessionId(), instanceCode, bucketName);
    }

    /** 切换桶状态。 */
    @Override
    public ConfigurationMutationResponseBody changeBucketStatus(OperatorContext context, String instanceCode,
        String bucketName, ChangeStorageStatusRequestBody request) {
        return write(request, () -> api.changeBucketStatus(context.accountId(), context.sessionId(), instanceCode,
            bucketName, request));
    }

    /** 写入路由列表原样返回。 */
    @Override
    public StorageRouteListResponseBody listRoutes(OperatorContext context) {
        return api.listRoutes(context.accountId(), context.sessionId());
    }

    /** 新建写入路由。 */
    @Override
    public ConfigurationMutationResponseBody createRoute(OperatorContext context, CreateStorageRouteRequestBody request) {
        return write(request, () -> api.createRoute(context.accountId(), context.sessionId(), request));
    }

    /** 修改写入路由。 */
    @Override
    public ConfigurationMutationResponseBody updateRoute(OperatorContext context, String sourceSystem,
        UpdateStorageRouteRequestBody request) {
        return write(request, () -> api.updateRoute(context.accountId(), context.sessionId(), sourceSystem, request));
    }

    /** 删除写入路由。 */
    @Override
    public ConfigurationMutationResponseBody deleteRoute(OperatorContext context, String sourceSystem,
        DeleteStorageRouteRequestBody request) {
        return write(request, () -> api.deleteRoute(context.accountId(), context.sessionId(), sourceSystem, request));
    }

    /** 执行一次写调用，把 Platform 字段错误投影到本次请求体。 */
    private static ConfigurationMutationResponseBody write(Object request,
        Supplier<ConfigurationMutationResponseBody> call) {
        try {
            return call.get();
        } catch (HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }
}
