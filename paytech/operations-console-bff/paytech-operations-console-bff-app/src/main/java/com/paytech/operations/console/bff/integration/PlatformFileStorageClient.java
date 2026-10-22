package com.paytech.operations.console.bff.integration;

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

/**
 * Platform 存储管理的本地端口；实例、桶、写入路由的规则、FILE_STORAGE_* 再次鉴权与审计归 Platform
 * <li>页面请求与响应直接使用 Platform 管理合同的模型，BFF 不增加页面字段；每次调用都携带会话 Account ID 与 Session ID。</li>
 * <li>新建实例与更换密钥的请求含存储密钥，BFF 只转发、不记录、不回显。</li>
 *
 * @author Claude
 */
public interface PlatformFileStorageClient {

    /**
     * 存储实例列表，含桶与被路由引用数；只返回是否已配置密钥
     *
     * @param context 操作人
     * @return 全部实例
     */
    StorageInstanceListResponseBody listInstances(OperatorContext context);

    /**
     * 新建 DRAFT 存储实例
     *
     * @param context 操作人
     * @param request 页面请求，含可选密钥
     * @return 保存回执，objectCode 为实例代码
     */
    ConfigurationMutationResponseBody createInstance(OperatorContext context, CreateStorageInstanceRequestBody request);

    /**
     * 按原版本修改实例连接设置
     *
     * @param context 操作人
     * @param instanceCode 实例代码
     * @param request 页面请求
     * @return 保存回执
     */
    ConfigurationMutationResponseBody updateInstance(OperatorContext context, String instanceCode,
        UpdateStorageInstanceRequestBody request);

    /**
     * 按原版本更换密钥或切换凭据方式
     *
     * @param context 操作人
     * @param instanceCode 实例代码
     * @param request 页面请求，含新密钥
     * @return 保存回执
     */
    ConfigurationMutationResponseBody replaceCredential(OperatorContext context, String instanceCode,
        ReplaceStorageCredentialRequestBody request);

    /**
     * 按原版本切换实例状态
     *
     * @param context 操作人
     * @param instanceCode 实例代码
     * @param request 页面请求
     * @return 保存回执
     */
    ConfigurationMutationResponseBody changeInstanceStatus(OperatorContext context, String instanceCode,
        ChangeStorageStatusRequestBody request);

    /**
     * 在实例下登记 DRAFT 桶
     *
     * @param context 操作人
     * @param instanceCode 实例代码
     * @param request 页面请求
     * @return 保存回执，objectCode 为 {实例代码}/{桶名}
     */
    ConfigurationMutationResponseBody registerBucket(OperatorContext context, String instanceCode,
        RegisterStorageBucketRequestBody request);

    /**
     * 检查桶并由 Platform 回写结果
     *
     * @param context 操作人
     * @param instanceCode 实例代码
     * @param bucketName 桶名
     * @return 检查结果与时间
     */
    StorageBucketCheckResponseBody checkBucket(OperatorContext context, String instanceCode, String bucketName);

    /**
     * 按原版本切换桶状态
     *
     * @param context 操作人
     * @param instanceCode 实例代码
     * @param bucketName 桶名
     * @param request 页面请求
     * @return 保存回执
     */
    ConfigurationMutationResponseBody changeBucketStatus(OperatorContext context, String instanceCode, String bucketName,
        ChangeStorageStatusRequestBody request);

    /**
     * 写入路由列表
     *
     * @param context 操作人
     * @return 全部路由
     */
    StorageRouteListResponseBody listRoutes(OperatorContext context);

    /**
     * 新建写入路由
     *
     * @param context 操作人
     * @param request 页面请求
     * @return 保存回执，objectCode 为来源系统
     */
    ConfigurationMutationResponseBody createRoute(OperatorContext context, CreateStorageRouteRequestBody request);

    /**
     * 按原版本修改写入路由
     *
     * @param context 操作人
     * @param sourceSystem 来源系统，默认路由为 *
     * @param request 页面请求
     * @return 保存回执
     */
    ConfigurationMutationResponseBody updateRoute(OperatorContext context, String sourceSystem,
        UpdateStorageRouteRequestBody request);

    /**
     * 按原版本删除写入路由；默认路由由 Platform 拒绝
     *
     * @param context 操作人
     * @param sourceSystem 来源系统
     * @param request 页面请求
     * @return 删除前的身份与版本
     */
    ConfigurationMutationResponseBody deleteRoute(OperatorContext context, String sourceSystem,
        DeleteStorageRouteRequestBody request);
}
